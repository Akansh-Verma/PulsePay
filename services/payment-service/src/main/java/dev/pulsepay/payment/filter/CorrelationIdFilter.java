package dev.pulsepay.payment.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter to populate MDC with correlation ID from request headers.
 * Enables logging with correlation ID tracking across the request lifecycle.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Extract or generate correlation ID
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Extract or generate trace ID
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Set values in MDC for logging
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Clean up MDC after request
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
