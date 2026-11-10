package com.ecommerce.payment.domain;

public enum PaymentStatus {
    PENDING, COMPLETED, FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
