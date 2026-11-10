package com.ecommerce.common.idempotency;

import java.time.Duration;
import java.util.Optional;

/** Storage abstraction so the filter can be unit-tested without Redis. */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    /** Atomically claims the key (SET NX). Returns false if another request already owns it. */
    boolean tryAcquire(String key, IdempotencyRecord inProgress, Duration ttl);

    void complete(String key, IdempotencyRecord completed, Duration ttl);

    void release(String key);
}
