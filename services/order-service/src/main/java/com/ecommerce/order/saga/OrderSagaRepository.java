package com.ecommerce.order.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {

    /**
     * SKIP LOCKED: when several order-service replicas run the timeout job at the same moment,
     * each one picks different sagas – no double processing, no blocking, no leader election.
     */
    @Query(value = """
            SELECT * FROM order_sagas
            WHERE status IN ('STARTED', 'COMPENSATING') AND deadline < :now
            ORDER BY deadline
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderSaga> lockExpired(Instant now, int limit);
}
