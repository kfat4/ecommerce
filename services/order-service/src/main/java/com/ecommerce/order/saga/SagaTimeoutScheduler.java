package com.ecommerce.order.saga;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Sagas must never wait forever: messages can be lost by bugs, a participant can be down for hours.
 * Runs on every replica; {@code FOR UPDATE SKIP LOCKED} distributes the work.
 */
@Component
public class SagaTimeoutScheduler {

    private final OrderSagaRepository sagas;
    private final OrderSagaOrchestrator orchestrator;

    public SagaTimeoutScheduler(OrderSagaRepository sagas, OrderSagaOrchestrator orchestrator) {
        this.sagas = sagas;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${ecommerce.saga.timeout-check-ms:10000}")
    @Transactional
    public void checkTimeouts() {
        sagas.lockExpired(Instant.now(), 50).forEach(orchestrator::onTimeout);
    }
}
