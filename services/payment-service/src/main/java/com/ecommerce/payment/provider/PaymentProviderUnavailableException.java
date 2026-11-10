package com.ecommerce.payment.provider;

public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
