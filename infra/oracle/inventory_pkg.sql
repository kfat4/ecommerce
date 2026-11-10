-- =====================================================================================================
-- Oracle PL/SQL equivalent of services/inventory-service/.../V2__stock_procedures.sql
-- Reference only (the running stack uses PostgreSQL). Shows how the same kernel is written as an Oracle
-- package – the dominant style in banks/telcos – so you can compare PL/pgSQL and PL/SQL side by side.
--
-- Differences worth noticing:
--   * Oracle groups related procedures in a PACKAGE (spec + body) – namespacing, private helpers, state.
--   * Collections are passed as SQL object/table types (here: t_order_line_tab) instead of JSONB.
--   * SELECT ... FOR UPDATE NOWAIT / WAIT n lets you bound lock waits explicitly.
--   * Errors are raised with RAISE_APPLICATION_ERROR(-20xxx, ...) and caught in Java as SQLException codes.
-- =====================================================================================================

CREATE OR REPLACE TYPE t_order_line AS OBJECT (product_id VARCHAR2(40), quantity NUMBER(10));
/
CREATE OR REPLACE TYPE t_order_line_tab AS TABLE OF t_order_line;
/

CREATE OR REPLACE PACKAGE inventory_pkg AS
    e_insufficient_stock EXCEPTION;
    PRAGMA EXCEPTION_INIT (e_insufficient_stock, -20001);

    PROCEDURE reserve_stock(p_order_id IN RAW, p_lines IN t_order_line_tab,
                            p_success OUT NUMBER, p_reason OUT VARCHAR2);
    FUNCTION release_stock(p_order_id IN RAW) RETURN NUMBER;
    FUNCTION commit_stock(p_order_id IN RAW) RETURN NUMBER;
END inventory_pkg;
/

CREATE OR REPLACE PACKAGE BODY inventory_pkg AS

    PROCEDURE reserve_stock(p_order_id IN RAW, p_lines IN t_order_line_tab,
                            p_success OUT NUMBER, p_reason OUT VARCHAR2) IS
        v_available inventory_items.available_quantity%TYPE;
        v_status    stock_reservations.status%TYPE;
    BEGIN
        -- Idempotency
        BEGIN
            SELECT status INTO v_status FROM stock_reservations WHERE order_id = p_order_id AND ROWNUM = 1;
            p_success := CASE WHEN v_status = 'RELEASED' THEN 0 ELSE 1 END;
            p_reason := CASE WHEN v_status = 'RELEASED' THEN 'ALREADY_RELEASED' ELSE 'ALREADY_RESERVED' END;
            RETURN;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN NULL;
        END;

        -- Validate + lock in deterministic order (deadlock prevention)
        FOR l IN (SELECT product_id, SUM(quantity) AS quantity
                  FROM TABLE(p_lines) GROUP BY product_id ORDER BY product_id)
        LOOP
            BEGIN
                SELECT available_quantity INTO v_available
                FROM inventory_items WHERE product_id = l.product_id
                FOR UPDATE WAIT 5;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    p_success := 0; p_reason := 'UNKNOWN_PRODUCT:' || l.product_id; RETURN;
            END;
            IF v_available < l.quantity THEN
                p_success := 0; p_reason := 'INSUFFICIENT_STOCK:' || l.product_id; RETURN;
            END IF;
        END LOOP;

        -- Apply
        FOR l IN (SELECT product_id, SUM(quantity) AS quantity
                  FROM TABLE(p_lines) GROUP BY product_id ORDER BY product_id)
        LOOP
            UPDATE inventory_items
               SET available_quantity = available_quantity - l.quantity,
                   reserved_quantity  = reserved_quantity + l.quantity,
                   updated_at         = SYSTIMESTAMP
             WHERE product_id = l.product_id;

            INSERT INTO stock_reservations (id, order_id, product_id, quantity, status, created_at, updated_at)
            VALUES (SYS_GUID(), p_order_id, l.product_id, l.quantity, 'RESERVED', SYSTIMESTAMP, SYSTIMESTAMP);
        END LOOP;

        p_success := 1;
        p_reason := 'RESERVED';
    END reserve_stock;

    FUNCTION release_stock(p_order_id IN RAW) RETURN NUMBER IS
        v_count NUMBER := 0;
    BEGIN
        FOR r IN (SELECT id, product_id, quantity FROM stock_reservations
                  WHERE order_id = p_order_id AND status = 'RESERVED'
                  ORDER BY product_id FOR UPDATE)
        LOOP
            UPDATE inventory_items
               SET available_quantity = available_quantity + r.quantity,
                   reserved_quantity  = reserved_quantity - r.quantity,
                   updated_at         = SYSTIMESTAMP
             WHERE product_id = r.product_id;
            UPDATE stock_reservations SET status = 'RELEASED', updated_at = SYSTIMESTAMP WHERE id = r.id;
            v_count := v_count + 1;
        END LOOP;
        RETURN v_count;
    END release_stock;

    FUNCTION commit_stock(p_order_id IN RAW) RETURN NUMBER IS
        v_count NUMBER := 0;
    BEGIN
        FOR r IN (SELECT id, product_id, quantity FROM stock_reservations
                  WHERE order_id = p_order_id AND status = 'RESERVED'
                  ORDER BY product_id FOR UPDATE)
        LOOP
            UPDATE inventory_items
               SET reserved_quantity = reserved_quantity - r.quantity,
                   updated_at        = SYSTIMESTAMP
             WHERE product_id = r.product_id;
            UPDATE stock_reservations SET status = 'COMMITTED', updated_at = SYSTIMESTAMP WHERE id = r.id;
            v_count := v_count + 1;
        END LOOP;
        RETURN v_count;
    END commit_stock;

END inventory_pkg;
/

-- Calling it from Java (Spring JDBC):
--   new SimpleJdbcCall(jdbcTemplate).withCatalogName("INVENTORY_PKG").withProcedureName("RESERVE_STOCK")
--       .execute(Map.of("P_ORDER_ID", orderIdBytes, "P_LINES", oracleArrayOfLines));
