package com.ecommerce.inventory.api;

import com.ecommerce.common.web.ResourceNotFoundException;
import com.ecommerce.inventory.infrastructure.StockRepository;
import com.ecommerce.inventory.infrastructure.StockRepository.StockLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final StockRepository stock;

    public InventoryController(StockRepository stock) {
        this.stock = stock;
    }

    @GetMapping("/{productId}")
    StockLevel get(@PathVariable String productId) {
        return stock.find(productId).orElseThrow(() -> new ResourceNotFoundException("Inventory item", productId));
    }

    /** PUT is naturally idempotent: setting the same absolute value twice has the same effect as once. */
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    StockLevel set(@PathVariable String productId, @Valid @RequestBody SetStockRequest request) {
        stock.upsert(productId, request.available());
        return stock.find(productId).orElseThrow();
    }

    public record SetStockRequest(@Min(0) int available) {
    }
}
