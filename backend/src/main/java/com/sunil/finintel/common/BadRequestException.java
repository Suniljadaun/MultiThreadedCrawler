package com.sunil.finintel.common;

// Invalid input that bean validation cannot express. Mapped to HTTP 400.
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
