package com.assessly.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public final class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = supplied == null || supplied.isBlank() || supplied.length() > 128
                ? UUID.randomUUID().toString() : supplied;
        request.setAttribute(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
            filterChain.doFilter(request, response);
        }
    }
}
