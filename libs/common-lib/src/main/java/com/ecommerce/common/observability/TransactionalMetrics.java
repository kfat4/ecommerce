package com.ecommerce.common.observability;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business metrics must describe what really happened. Inside a transaction, "order confirmed" only happened if
 * the transaction commits – a rollback followed by a Kafka redelivery would otherwise count the same event twice.
 */
public final class TransactionalMetrics {

    private TransactionalMetrics() {
    }

    /** Runs {@code action} after the current transaction commits, or immediately if there is no transaction. */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
