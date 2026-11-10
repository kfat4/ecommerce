package com.ecommerce.common.idempotency;

public record IdempotencyRecord(State state, String requestHash, int status, String contentType, String body) {

    public enum State { IN_PROGRESS, COMPLETED }

    public static IdempotencyRecord inProgress(String requestHash) {
        return new IdempotencyRecord(State.IN_PROGRESS, requestHash, 0, null, null);
    }
}
