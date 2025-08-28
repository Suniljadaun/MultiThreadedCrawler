package com.sunil.finintel.common;

// Thrown when a request conflicts with existing data (e.g. duplicate email). Mapped to HTTP 409.
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
