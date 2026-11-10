package com.ecommerce.inventory;

import com.ecommerce.common.contracts.OrderLine;
import com.ecommerce.inventory.infrastructure.StockRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations + PL/pgSQL functions against a real PostgreSQL (Testcontainers).
 * Database code deserves tests too – especially concurrency-critical code.
 */
@Testcontainers(disabledWithoutDocker = true)
class StockProceduresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static JdbcTemplate jdbc;
    static StockRepository stock;

    @BeforeAll
    static void migrate() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        stock = new StockRepository(jdbc, JsonMapper.builder().build());
    }

    @Test
    void reservesAllLinesOrNothing() {
        jdbc.update("INSERT INTO inventory_items (product_id, available_quantity) VALUES ('T-1', 5), ('T-2', 1)");
        UUID order = UUID.randomUUID();

        var result = stock.reserve(order, List.of(line("T-1", 2), line("T-2", 2)));

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo("INSUFFICIENT_STOCK:T-2");
        assertThat(stock.find("T-1").orElseThrow().available()).isEqualTo(5); // untouched: all-or-nothing
    }

    @Test
    void reserveIsIdempotentAndReleaseGivesStockBack() {
        jdbc.update("INSERT INTO inventory_items (product_id, available_quantity) VALUES ('T-3', 10)");
        UUID order = UUID.randomUUID();

        assertThat(stock.reserve(order, List.of(line("T-3", 4))).reason()).isEqualTo("RESERVED");
        assertThat(stock.reserve(order, List.of(line("T-3", 4))).reason()).isEqualTo("ALREADY_RESERVED");
        assertThat(stock.find("T-3").orElseThrow().available()).isEqualTo(6);

        assertThat(stock.release(order)).isEqualTo(1);
        assertThat(stock.release(order)).isZero(); // idempotent compensation
        assertThat(stock.find("T-3").orElseThrow().available()).isEqualTo(10);

        // a late reserve command after compensation must not reserve again
        assertThat(stock.reserve(order, List.of(line("T-3", 4))).reason()).isEqualTo("ALREADY_RELEASED");
    }

    @Test
    void neverOversellsUnderConcurrency() throws Exception {
        jdbc.update("INSERT INTO inventory_items (product_id, available_quantity) VALUES ('T-4', 10)");
        List<Callable<Boolean>> buyers = java.util.stream.IntStream.range(0, 30)
                .<Callable<Boolean>>mapToObj(i -> () -> stock.reserve(UUID.randomUUID(), List.of(line("T-4", 1))).success())
                .toList();

        try (var pool = Executors.newFixedThreadPool(10)) {
            long successes = pool.invokeAll(buyers).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();
            assertThat(successes).isEqualTo(10);
        }
        assertThat(stock.find("T-4").orElseThrow().available()).isZero();
    }

    private static OrderLine line(String productId, int quantity) {
        return new OrderLine(productId, quantity, BigDecimal.ONE);
    }
}
