package com.ecommerce.product.api;

import com.ecommerce.product.domain.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductResponse(String id, String sku, String name, String description, String category,
                                  BigDecimal price, String currency, Map<String, String> attributes, boolean active) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getCategory(),
                    p.getPrice(), p.getCurrency(), p.getAttributes(), p.isActive());
        }
    }

    /** Internal export row: the full state incl. aggregate version and soft-delete flag. */
    public record ProductExport(String id, long version, String sku, String name, String description,
                                String category, BigDecimal price, String currency, Map<String, String> attributes,
                                boolean active, boolean deleted) {

        public static ProductExport from(Product p) {
            return new ProductExport(p.getId(), p.getVersion(), p.getSku(), p.getName(), p.getDescription(),
                    p.getCategory(), p.getPrice(), p.getCurrency(), p.getAttributes(), p.isActive(), p.isDeleted());
        }
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotBlank String category,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            Map<String, String> attributes) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Map<String, String> attributes,
            boolean active) {
    }
}
