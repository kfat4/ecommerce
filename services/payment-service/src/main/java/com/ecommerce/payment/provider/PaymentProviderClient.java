package com.ecommerce.payment.provider;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client for an EXTERNAL payment provider (simulated by WireMock, see infra/wiremock).
 * <p>
 * The single most important best practice here: <b>the Idempotency-Key header</b>. A POST that timed out may or
 * may not have charged the card. Retrying without a key could charge twice; with the key the provider returns
 * the original result. That is what makes {@code @Retry} on a non-idempotent HTTP method safe.
 */
@Component
public class PaymentProviderClient {

    static final String NAME = "paymentProvider";

    private final RestClient restClient;

    public PaymentProviderClient(RestClient.Builder builder, @Value("${payment.provider.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Retry(name = NAME)
    @CircuitBreaker(name = NAME, fallbackMethod = "providerUnavailable")
    @Bulkhead(name = NAME)
    public ChargeResponse charge(UUID orderId, BigDecimal amount, String currency) {
        return restClient.post()
                .uri("/v1/charges")
                .header("Idempotency-Key", "order-" + orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChargeRequest(orderId.toString(), amount, currency))
                .retrieve()
                .body(ChargeResponse.class);
    }

    @SuppressWarnings("unused")
    private ChargeResponse providerUnavailable(UUID orderId, BigDecimal amount, String currency, Throwable cause) {
        // Transient: let Kafka redeliver the command later (DefaultErrorHandler back-off → DLT).
        throw new PaymentProviderUnavailableException("Payment provider unavailable: " + cause.getMessage(), cause);
    }

    public record ChargeRequest(String reference, BigDecimal amount, String currency) {
    }

    public record ChargeResponse(String id, String status, String declineReason) {

        public boolean approved() {
            return "APPROVED".equals(status);
        }
    }
}
