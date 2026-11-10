package com.ecommerce.common.web;

/** A downstream dependency is unavailable; the client may retry later (HTTP 503 + Retry-After). */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
