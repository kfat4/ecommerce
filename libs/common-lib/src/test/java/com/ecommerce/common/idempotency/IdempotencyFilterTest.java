package com.ecommerce.common.idempotency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyFilterTest {

    private final InMemoryStore store = new InMemoryStore();
    private final AtomicInteger executions = new AtomicInteger();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(store,
                new IdempotencyProperties(true, List.of("/api/v1/orders"), Duration.ofHours(1), Duration.ofSeconds(30)),
                "order-service", meterRegistry);
    }

    @Test
    void rejectsRequestWithoutKey() throws Exception {
        MockHttpServletResponse response = execute(request(null, "{\"a\":1}"), 202);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(executions).hasValue(0);
    }

    @Test
    void replaysStoredResponseInsteadOfExecutingTwice() throws Exception {
        MockHttpServletResponse first = execute(request("key-12345678", "{\"a\":1}"), 202);
        MockHttpServletResponse second = execute(request("key-12345678", "{\"a\":1}"), 202);

        assertThat(executions).hasValue(1);
        assertThat(second.getStatus()).isEqualTo(202);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(second.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(meterRegistry.get("ecommerce.idempotency.requests").tag("outcome", "replayed").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void rejectsSameKeyWithDifferentPayload() throws Exception {
        execute(request("key-12345678", "{\"a\":1}"), 202);
        MockHttpServletResponse second = execute(request("key-12345678", "{\"a\":2}"), 202);

        assertThat(second.getStatus()).isEqualTo(422);
        assertThat(executions).hasValue(1);
    }

    @Test
    void returnsConflictWhileFirstRequestIsInProgress() throws Exception {
        store.tryAcquire("idempotency:order-service:anonymous:key-12345678",
                IdempotencyRecord.inProgress(IdempotencyFilter.hash("POST", "/api/v1/orders",
                        "{\"a\":1}".getBytes(StandardCharsets.UTF_8))), Duration.ofSeconds(30));

        MockHttpServletResponse response = execute(request("key-12345678", "{\"a\":1}"), 202);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(executions).hasValue(0);
    }

    @Test
    void serverErrorsReleaseTheKeySoTheClientCanRetry() throws Exception {
        execute(request("key-12345678", "{\"a\":1}"), 503);
        MockHttpServletResponse retry = execute(request("key-12345678", "{\"a\":1}"), 202);

        assertThat(executions).hasValue(2);
        assertThat(retry.getStatus()).isEqualTo(202);
    }

    @Test
    void ignoresPathsThatAreNotConfigured() throws Exception {
        MockHttpServletRequest request = request(null, "{}");
        request.setRequestURI("/api/v1/products");

        assertThat(execute(request, 200).getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        return request;
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            executions.incrementAndGet();
            req.getInputStream().readAllBytes(); // controller reads the (cached) body
            ((HttpServletResponse) res).setStatus(status);
            res.setContentType("application/json");
            res.getWriter().write("{\"orderId\":\"" + executions.get() + "\"}");
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    private static final class InMemoryStore implements IdempotencyStore {

        private final Map<String, IdempotencyRecord> data = new ConcurrentHashMap<>();

        @Override
        public Optional<IdempotencyRecord> find(String key) {
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public boolean tryAcquire(String key, IdempotencyRecord inProgress, Duration ttl) {
            return data.putIfAbsent(key, inProgress) == null;
        }

        @Override
        public void complete(String key, IdempotencyRecord completed, Duration ttl) {
            data.put(key, completed);
        }

        @Override
        public void release(String key) {
            data.remove(key);
        }
    }
}
