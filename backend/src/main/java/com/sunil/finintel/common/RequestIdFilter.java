package com.sunil.finintel.common;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Runs first: sets the request id (client's X-Request-ID or a new UUID), echoes it back,
// and writes one access log line per request.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = RequestIds.sanitize(request.getHeader(RequestIds.HEADER));
        MDC.put(RequestIds.MDC_KEY, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // Prometheus scrapes /actuator every few seconds; logging those would drown everything else
            if (!request.getRequestURI().startsWith("/actuator")) {
                log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), (System.nanoTime() - started) / 1_000_000);
            }
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
