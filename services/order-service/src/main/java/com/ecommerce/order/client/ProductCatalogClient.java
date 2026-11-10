package com.ecommerce.order.client;

import com.ecommerce.common.web.ServiceUnavailableException;
import com.ecommerce.order.client.ProductCatalogApi.ProductSnapshot;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resilient adapter around the remote call. Web-service call best practices applied here:
 * <ol>
 *   <li><b>Timeouts</b> – connect 1s / read 2s (spring.http.clients.*). A call without timeout is a resource leak.</li>
 *   <li><b>Retry with exponential back-off</b> – only for transient errors (I/O, 5xx), never for 4xx.
 *       GET is idempotent, so retrying is safe.</li>
 *   <li><b>Circuit breaker</b> – stop hammering a failing dependency, fail fast, let it recover.</li>
 *   <li><b>Bulkhead</b> – cap concurrent calls so a slow product-service cannot exhaust our threads.</li>
 *   <li><b>Fallback</b> – a clear 503 + Retry-After to the client instead of a stack trace.</li>
 *   <li><b>Client-side load balancing</b> – Eureka + Spring Cloud LoadBalancer spread calls over replicas.</li>
 *   <li><b>Batch API</b> – one request for N products (no N+1 remote calls).</li>
 * </ol>
 * Aspect order (outer → inner): Retry → CircuitBreaker → Bulkhead → call.
 */
@Component
public class ProductCatalogClient {

    static final String NAME = "productService";

    private final ProductCatalogApi api;

    public ProductCatalogClient(ProductCatalogApi api) {
        this.api = api;
    }

    @Retry(name = NAME)
    @CircuitBreaker(name = NAME, fallbackMethod = "unavailable")
    @Bulkhead(name = NAME)
    public List<ProductSnapshot> findByIds(List<String> ids) {
        return api.findByIds(ids);
    }

    @SuppressWarnings("unused")
    private List<ProductSnapshot> unavailable(List<String> ids, Throwable cause) {
        throw new ServiceUnavailableException("Product catalogue is unavailable, please retry later.", cause);
    }
}
