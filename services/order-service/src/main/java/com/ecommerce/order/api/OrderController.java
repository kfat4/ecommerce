package com.ecommerce.order.api;

import com.ecommerce.common.idempotency.IdempotencyFilter;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.order.api.OrderDtos.OrderResponse;
import com.ecommerce.order.api.OrderDtos.PlaceOrderRequest;
import com.ecommerce.order.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Asynchronous request-reply: 202 Accepted + Location header. The client polls GET /orders/{id}
     * (or, in a real system, gets a push notification) until the saga reaches CONFIRMED or CANCELLED.
     * The Idempotency-Key header is enforced by the IdempotencyFilter (common-lib).
     */
    @PostMapping
    ResponseEntity<OrderResponse> place(@RequestHeader(IdempotencyFilter.HEADER) String idempotencyKey,
                                        @Valid @RequestBody PlaceOrderRequest request) {
        var result = orderService.placeOrder(CurrentUser.id(), idempotencyKey, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(result.order().id()).toUri();
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(location)
                .body(result.order());
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id, CurrentUser.id(), CurrentUser.isAdmin());
    }

    @GetMapping
    PageResponse<OrderResponse> mine(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return orderService.mine(CurrentUser.id(), page, size);
    }
}
