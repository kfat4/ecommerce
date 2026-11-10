package com.ecommerce.product.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Why a document store for the catalogue? Products are read-heavy, have a flexible attribute set per category
 * (a T-shirt has size/colour, a laptop has CPU/RAM) and are always loaded as a whole aggregate – a natural fit
 * for a document. Orders/payments, which need multi-row ACID transactions, stay in PostgreSQL.
 */
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private String name;

    private String description;

    @Indexed
    private String category;

    /** Stored as Decimal128 – never use double for money. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    private String currency;

    /** Schemaless, category-specific attributes. */
    private Map<String, String> attributes;

    private boolean active;

    /**
     * Soft delete. Products are referenced by historical orders and by read models (search index), so they are
     * never physically removed: the version keeps growing, which lets consumers order events reliably.
     */
    private boolean deleted;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String id, String sku, String name, String description, String category, BigDecimal price,
                   String currency, Map<String, String> attributes) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.currency = currency;
        this.attributes = attributes;
        this.active = true;
    }

    public void update(String name, String description, BigDecimal price, Map<String, String> attributes, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.attributes = attributes;
        this.active = active;
    }

    public void markDeleted() {
        this.deleted = true;
        this.active = false;
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public Map<String, String> getAttributes() { return attributes; }
    public boolean isActive() { return active; }
    public boolean isDeleted() { return deleted; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
