package com.sunil.finintel.messaging;

// A message that can never be processed. Not retried; sent straight to the DLT.
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
