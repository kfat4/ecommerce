package com.ecommerce.order.application;

import com.ecommerce.common.util.UuidV7;
import com.ecommerce.common.web.BusinessException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.order.api.OrderDtos.OrderResponse;
import com.ecommerce.order.api.OrderDtos.PlaceOrderRequest;
import com.ecommerce.order.client.ProductCatalogApi.ProductSnapshot;
import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.saga.OrderSagaOrchestrator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final ProductCatalogClient catalog;
    private final OrderSagaOrchestrator saga;
    private final TransactionTemplate transaction;

    public OrderService(OrderRepository orders, ProductCatalogClient catalog, OrderSagaOrchestrator saga,
                        TransactionTemplate transaction) {
        this.orders = orders;
        this.catalog = catalog;
        this.saga = saga;
        this.transaction = transaction;
    }

    /**
     * 1. Remote call to price the order – deliberately OUTSIDE any DB transaction:
     *    holding a DB connection while waiting on the network is a classic scalability killer.
     * 2. One short local transaction: insert order + saga state + outbox command (atomic).
     * 3. Return immediately (202 Accepted); the saga continues asynchronously.
     */
    public PlaceOrderResult placeOrder(UUID customerId, String idempotencyKey, PlaceOrderRequest request) {
        Optional<Order> existing = orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return new PlaceOrderResult(OrderResponse.from(existing.get()), false);
        }

        PricedItems priced = priceItems(request);
        Order order = new Order(UuidV7.next(), customerId, priced.currency(), idempotencyKey, priced.items());
        try {
            transaction.executeWithoutResult(status -> {
                orders.save(order);
                saga.start(order);
            });
            return new PlaceOrderResult(OrderResponse.from(order), true);
        } catch (DataIntegrityViolationException duplicate) {
            // Concurrent retry with the same key on another replica won the race – return its result.
            return orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                    .map(o -> new PlaceOrderResult(OrderResponse.from(o), false))
                    .orElseThrow(() -> duplicate);
        }
    }

    public OrderResponse get(UUID orderId, UUID callerId, boolean admin) {
        Order order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!admin && !order.getCustomerId().equals(callerId)) {
            // Respond like "not found"? We use 403 for clarity; hiding existence (404) is also a valid choice.
            throw new AccessDeniedException("Not your order");
        }
        return OrderResponse.from(order);
    }

    public PageResponse<OrderResponse> mine(UUID customerId, int page, int size) {
        return PageResponse.of(orders.findByCustomerIdOrderByCreatedAtDesc(customerId,
                PageRequest.of(page, Math.min(size, 100))), OrderResponse::from);
    }

    private PricedItems priceItems(PlaceOrderRequest request) {
        List<String> ids = request.items().stream().map(PlaceOrderRequest.Line::productId).distinct().toList();
        Map<String, ProductSnapshot> products = catalog.findByIds(ids).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));

        for (String id : ids) {
            ProductSnapshot p = products.get(id);
            if (p == null || !p.active()) {
                throw new BusinessException("product-unavailable", "Product " + id + " does not exist or is not for sale.");
            }
        }
        long currencies = products.values().stream().map(ProductSnapshot::currency).distinct().count();
        if (currencies > 1) {
            throw new BusinessException("mixed-currencies", "All products of an order must have the same currency.");
        }
        List<OrderItem> items = request.items().stream().map(line -> {
            ProductSnapshot p = products.get(line.productId());
            return new OrderItem(p.id(), p.name(), line.quantity(), p.price());
        }).toList();
        return new PricedItems(items, products.values().iterator().next().currency());
    }

    private record PricedItems(List<OrderItem> items, String currency) {
    }

    public record PlaceOrderResult(OrderResponse order, boolean created) {
    }
}
