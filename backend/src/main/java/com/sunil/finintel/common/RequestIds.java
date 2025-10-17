package com.sunil.finintel.common;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

// Request id = correlation id. Set per HTTP request, copied into every event the request causes,
// and restored by the consumers, so one id follows an order through all components (docs/observability.md).
public final class RequestIds {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    // Only reuse a client's id if it is short and harmless to put in logs
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private RequestIds() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    static String sanitize(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    // Puts the id in the MDC until close(). A null id leaves the MDC unchanged.
    public static MDC.MDCCloseable bind(String requestId) {
        if (requestId == null) {
            return null;
        }
        return MDC.putCloseable(MDC_KEY, requestId);
    }
}
