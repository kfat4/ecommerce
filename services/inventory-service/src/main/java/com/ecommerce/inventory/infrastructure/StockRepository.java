package com.ecommerce.inventory.infrastructure;

import com.ecommerce.common.contracts.OrderLine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Thin JDBC adapter over the PL/pgSQL functions (see V2__stock_procedures.sql). */
@Repository
public class StockRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StockRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public ReservationResult reserve(UUID orderId, List<OrderLine> lines) {
        String json = objectMapper.writeValueAsString(lines.stream()
                .map(l -> Map.of("productId", l.productId(), "quantity", l.quantity()))
                .toList());
        return jdbc.queryForObject("SELECT success, reason FROM reserve_stock(?, CAST(? AS jsonb))",
                (rs, i) -> new ReservationResult(rs.getBoolean("success"), rs.getString("reason")), orderId, json);
    }

    public int release(UUID orderId) {
        return jdbc.queryForObject("SELECT release_stock(?)", Integer.class, orderId);
    }

    public int commit(UUID orderId) {
        return jdbc.queryForObject("SELECT commit_stock(?)", Integer.class, orderId);
    }

    public Optional<StockLevel> find(String productId) {
        return jdbc.query("SELECT product_id, available_quantity, reserved_quantity FROM inventory_items WHERE product_id = ?",
                (rs, i) -> new StockLevel(rs.getString(1), rs.getInt(2), rs.getInt(3)), productId).stream().findFirst();
    }

    public void upsert(String productId, int available) {
        jdbc.update("""
                INSERT INTO inventory_items (product_id, available_quantity) VALUES (?, ?)
                ON CONFLICT (product_id) DO UPDATE SET available_quantity = EXCLUDED.available_quantity, updated_at = now()
                """, productId, available);
    }

    public record ReservationResult(boolean success, String reason) {
    }

    public record StockLevel(String productId, int available, int reserved) {
    }
}
