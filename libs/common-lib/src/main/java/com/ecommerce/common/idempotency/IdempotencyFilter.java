package com.ecommerce.common.idempotency;

import com.ecommerce.common.idempotency.IdempotencyRecord.State;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HTTP idempotency following the IETF "Idempotency-Key" header draft (as used by Stripe, Adyen, PayPal).
 * <p>
 * A mobile client that times out does not know whether its "place order" request was processed. With this filter
 * it can safely resend the very same request with the same key:
 * <ul>
 *   <li>first request: key is claimed (SET NX) → request processed → response stored for {@code ttl}</li>
 *   <li>retry after completion: stored response is replayed, nothing is executed twice</li>
 *   <li>retry while the first one is still running: 409 Conflict</li>
 *   <li>same key with a different body: 422 (client bug)</li>
 *   <li>5xx responses are NOT stored → the key is released and the client may retry</li>
 * </ul>
 * Keys are scoped per authenticated user, so one user can never replay another user's response.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final IdempotencyStore store;
    private final IdempotencyProperties properties;
    private final String keyPrefix;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final MeterRegistry meterRegistry;

    public IdempotencyFilter(IdempotencyStore store, IdempotencyProperties properties, String serviceName,
                             MeterRegistry meterRegistry) {
        this.store = store;
        this.properties = properties;
        this.keyPrefix = "idempotency:" + serviceName + ":";
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METHODS.contains(request.getMethod())
                || properties.paths().stream().noneMatch(p -> matcher.match(p, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientKey = request.getHeader(HEADER);
        if (clientKey == null || !VALID_KEY.matcher(clientKey).matches()) {
            count(Outcome.MISSING_KEY);
            writeProblem(response, HttpStatus.BAD_REQUEST,
                    "Header '" + HEADER + "' is required (8-100 chars, [A-Za-z0-9_-]), e.g. a UUID.");
            return;
        }

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String requestHash = hash(request.getMethod(), request.getRequestURI(), cachedRequest.body());
        String key = keyPrefix + principal() + ":" + clientKey;

        Optional<IdempotencyRecord> existing = store.find(key);
        if (existing.isPresent()) {
            handleExisting(existing.get(), requestHash, response);
            return;
        }
        if (!store.tryAcquire(key, IdempotencyRecord.inProgress(requestHash), properties.lockTtl())) {
            // Lost the race against a concurrent duplicate.
            count(Outcome.IN_PROGRESS);
            writeProblem(response, HttpStatus.CONFLICT, "A request with this Idempotency-Key is already in progress.");
            return;
        }

        count(Outcome.EXECUTED);
        var wrappedResponse = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(cachedRequest, wrappedResponse);
            int status = wrappedResponse.getStatus();
            if (status < 500) {
                String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                store.complete(key, new IdempotencyRecord(State.COMPLETED, requestHash, status,
                        wrappedResponse.getContentType(), body), properties.ttl());
                stored = true;
            }
        } finally {
            if (!stored) {
                store.release(key);
            }
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void handleExisting(IdempotencyRecord record, String requestHash, HttpServletResponse response)
            throws IOException {
        if (!record.requestHash().equals(requestHash)) {
            count(Outcome.PAYLOAD_MISMATCH);
            writeProblem(response, HttpStatus.UNPROCESSABLE_CONTENT,
                    "Idempotency-Key was already used with a different request payload.");
            return;
        }
        if (record.state() == State.IN_PROGRESS) {
            count(Outcome.IN_PROGRESS);
            response.setHeader("Retry-After", "1");
            writeProblem(response, HttpStatus.CONFLICT, "A request with this Idempotency-Key is already in progress.");
            return;
        }
        count(Outcome.REPLAYED);
        log.info("Replaying stored response for idempotent request (status={})", record.status());
        response.setStatus(record.status());
        response.setHeader(REPLAYED_HEADER, "true");
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        if (record.body() != null) {
            response.getOutputStream().write(record.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** {@code ecommerce_idempotency_requests_total{outcome}}: how often clients really retry, and how it ends. */
    private void count(Outcome outcome) {
        Counter.builder("ecommerce.idempotency.requests")
                .description("Requests handled by the idempotency filter")
                .tag("outcome", outcome.tag)
                .register(meterRegistry)
                .increment();
    }

    enum Outcome {
        EXECUTED("executed"), REPLAYED("replayed"), IN_PROGRESS("in_progress"),
        PAYLOAD_MISMATCH("payload_mismatch"), MISSING_KEY("missing_key");

        final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }
    }

    private static String principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }

    static String hash(String method, String uri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String json = "{\"type\":\"https://errors.ecommerce.example/idempotency\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(status.getReasonPhrase(), status.value(), detail.replace("\"", "'"));
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
