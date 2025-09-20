package com.sunil.finintel.common;

// Well-formed request that cannot be processed (e.g. idempotency key reused with a different body). Mapped to HTTP 422.
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
