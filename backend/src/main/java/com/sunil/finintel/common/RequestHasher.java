package com.sunil.finintel.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// SHA-256 of a string as 64 lowercase hex chars. Used to fingerprint requests for idempotency.
public final class RequestHasher {

    private RequestHasher() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM must support SHA-256, so this cannot happen in practice
            throw new IllegalStateException(e);
        }
    }
}
