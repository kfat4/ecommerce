package com.ecommerce.order.api;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** Note: the client sends NO prices. Prices always come from the catalogue (never trust the client). */
    public record PlaceOrderRequest(@NotEmpty @Size(max = 50) List<@Valid Line> items) {

        public record Line(@NotBlank String productId, @Min(1) @Max(100) int quantity) {
        }
    }

    public record OrderResponse(UUID id, OrderStatus status, BigDecimal totalAmount, String currency,
                                String failureReason, Instant createdAt, Instant updatedAt, List<Item> items) {

        public record Item(String productId, String productName, int quantity, BigDecimal unitPrice) {
        }

        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                    o.getFailureReason(), o.getCreatedAt(), o.getUpdatedAt(),
                    o.getItems().stream()
                            .map(i -> new Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                            .toList());
        }
    }
}
