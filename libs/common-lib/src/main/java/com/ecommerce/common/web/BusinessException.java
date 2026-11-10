package com.ecommerce.common.web;

/** A domain rule was violated (HTTP 422). The code is stable and machine-readable; the message is for humans. */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
