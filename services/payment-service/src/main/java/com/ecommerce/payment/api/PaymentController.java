package com.ecommerce.payment.api;

import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.payment.domain.PaymentRepository;
import com.ecommerce.payment.domain.PaymentStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/order/{orderId}")
    PaymentResponse byOrder(@PathVariable UUID orderId) {
        var payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
        if (!CurrentUser.isAdmin() && !payment.getCustomerId().equals(CurrentUser.id())) {
            throw new AccessDeniedException("Not your payment");
        }
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getCurrency(),
                payment.getStatus(), payment.getProviderReference(), payment.getFailureReason(), payment.getCreatedAt());
    }

    public record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, String currency, PaymentStatus status,
                                  String providerReference, String failureReason, Instant createdAt) {
    }
}
