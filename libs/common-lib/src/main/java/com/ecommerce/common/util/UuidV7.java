package com.ecommerce.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: time-ordered identifiers.
 * Unlike random v4 UUIDs they keep B-tree indexes compact (inserts land at the right edge),
 * which matters for append-heavy tables such as the outbox.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID next() {
        long millis = System.currentTimeMillis();
        long msb = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
