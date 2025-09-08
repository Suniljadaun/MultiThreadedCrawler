package com.sunil.finintel.common;

import java.time.Instant;

// Error body returned by every endpoint (see docs/api.md)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId) {
}
