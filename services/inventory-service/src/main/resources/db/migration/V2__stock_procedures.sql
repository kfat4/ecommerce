-- =====================================================================================================
-- Stock procedures in PL/pgSQL (PostgreSQL's procedural language, the counterpart of Oracle PL/SQL).
-- The Oracle PL/SQL equivalent of this package lives in infra/oracle/inventory_pkg.sql.
--
-- Why put this logic in the database?
--   * The check ("is there enough stock?") and the update happen under the same row locks, in one round trip.
--   * Concurrency-critical invariants (never oversell) are enforced next to the data.
-- Trade-off: business logic in the DB is harder to unit test / version / scale out. Use it for small,
-- concurrency-critical kernels like this, not for whole workflows (see docs/adr/0005-plsql-for-stock.md).
-- =====================================================================================================

-- Reserve all lines of an order atomically: either every line is reserved or none is.
CREATE OR REPLACE FUNCTION reserve_stock(p_order_id UUID, p_lines JSONB)
    RETURNS TABLE
            (
                success BOOLEAN,
                reason  TEXT
            )
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_line      RECORD;
    v_available INTEGER;
    v_status    VARCHAR(16);
BEGIN
    -- 0) The order was already compensated (e.g. after a saga timeout): never reserve for it again.
    IF EXISTS (SELECT 1 FROM released_orders ro WHERE ro.order_id = p_order_id) THEN
        RETURN QUERY SELECT FALSE, 'ALREADY_RELEASED'::TEXT;
        RETURN;
    END IF;

    -- 1) Idempotency: a redelivered command for an already handled order must not reserve twice.
    SELECT r.status INTO v_status FROM stock_reservations r WHERE r.order_id = p_order_id LIMIT 1;
    IF FOUND THEN
        IF v_status = 'RELEASED' THEN
            RETURN QUERY SELECT FALSE, 'ALREADY_RELEASED'::TEXT;
        ELSE
            RETURN QUERY SELECT TRUE, 'ALREADY_RESERVED'::TEXT;
        END IF;
        RETURN;
    END IF;

    -- 2) Validate + lock. Rows are locked in product_id order: every transaction acquires locks in the same
    --    order, which makes deadlocks between two concurrent orders impossible.
    FOR v_line IN
        SELECT l."productId" AS product_id, SUM(l.quantity)::INTEGER AS quantity
        FROM jsonb_to_recordset(p_lines) AS l("productId" TEXT, quantity INTEGER)
        GROUP BY l."productId"
        ORDER BY l."productId"
        LOOP
            SELECT i.available_quantity
            INTO v_available
            FROM inventory_items i
            WHERE i.product_id = v_line.product_id
                FOR UPDATE;

            IF NOT FOUND THEN
                RETURN QUERY SELECT FALSE, ('UNKNOWN_PRODUCT:' || v_line.product_id)::TEXT;
                RETURN;
            END IF;
            IF v_available < v_line.quantity THEN
                RETURN QUERY SELECT FALSE, ('INSUFFICIENT_STOCK:' || v_line.product_id)::TEXT;
                RETURN;
            END IF;
        END LOOP;

    -- 3) Apply. All rows are already locked by us, nobody can change them in between.
    FOR v_line IN
        SELECT l."productId" AS product_id, SUM(l.quantity)::INTEGER AS quantity
        FROM jsonb_to_recordset(p_lines) AS l("productId" TEXT, quantity INTEGER)
        GROUP BY l."productId"
        ORDER BY l."productId"
        LOOP
            UPDATE inventory_items
            SET available_quantity = available_quantity - v_line.quantity,
                reserved_quantity  = reserved_quantity + v_line.quantity,
                updated_at         = now()
            WHERE product_id = v_line.product_id;

            INSERT INTO stock_reservations (id, order_id, product_id, quantity, status)
            VALUES (gen_random_uuid(), p_order_id, v_line.product_id, v_line.quantity, 'RESERVED');
        END LOOP;

    RETURN QUERY SELECT TRUE, 'RESERVED'::TEXT;
END;
$$;

-- Compensation: give reserved stock back. Idempotent – only RESERVED rows are touched.
CREATE OR REPLACE FUNCTION release_stock(p_order_id UUID)
    RETURNS INTEGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_res   RECORD;
    v_count INTEGER := 0;
BEGIN
    FOR v_res IN
        SELECT r.id, r.product_id, r.quantity
        FROM stock_reservations r
        WHERE r.order_id = p_order_id
          AND r.status = 'RESERVED'
        ORDER BY r.product_id
            FOR UPDATE
        LOOP
            UPDATE inventory_items
            SET available_quantity = available_quantity + v_res.quantity,
                reserved_quantity  = reserved_quantity - v_res.quantity,
                updated_at         = now()
            WHERE product_id = v_res.product_id;

            UPDATE stock_reservations SET status = 'RELEASED', updated_at = now() WHERE id = v_res.id;
            v_count := v_count + 1;
        END LOOP;
    INSERT INTO released_orders (order_id) VALUES (p_order_id) ON CONFLICT DO NOTHING;
    RETURN v_count;
END;
$$;

-- Order confirmed: reserved stock becomes sold stock. Idempotent as well.
CREATE OR REPLACE FUNCTION commit_stock(p_order_id UUID)
    RETURNS INTEGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_res   RECORD;
    v_count INTEGER := 0;
BEGIN
    FOR v_res IN
        SELECT r.id, r.product_id, r.quantity
        FROM stock_reservations r
        WHERE r.order_id = p_order_id
          AND r.status = 'RESERVED'
        ORDER BY r.product_id
            FOR UPDATE
        LOOP
            UPDATE inventory_items
            SET reserved_quantity = reserved_quantity - v_res.quantity,
                updated_at        = now()
            WHERE product_id = v_res.product_id;

            UPDATE stock_reservations SET status = 'COMMITTED', updated_at = now() WHERE id = v_res.id;
            v_count := v_count + 1;
        END LOOP;
    RETURN v_count;
END;
$$;
