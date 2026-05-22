package dev.pulsepay.kafka.consumer;

import dev.pulsepay.events.PulsePayEventMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;

@Slf4j
public abstract class BaseEventConsumer {

    protected static final String MDC_TRACE_ID       = "traceId";
    protected static final String MDC_CORRELATION_ID = "correlationId";
    protected static final String MDC_EVENT_ID       = "eventId";
    protected static final String MDC_EVENT_TYPE     = "eventType";

    /**
     * Extract correlationId and traceId from event metadata and put them
     * into SLF4J MDC so every log line from this consumer automatically
     * includes them — making logs correlatable in Kibana and Zipkin.
     *
     * Call this at the start of every @KafkaListener method.
     * Always pair with clearContext() in a finally block.
     */
    protected void populateContext(PulsePayEventMetadata metadata) {
        MDC.put(MDC_TRACE_ID,       nullSafe(metadata.getTraceId()));
        MDC.put(MDC_CORRELATION_ID, nullSafe(metadata.getCorrelationId()));
        MDC.put(MDC_EVENT_ID,       nullSafe(metadata.getEventId()));
        MDC.put(MDC_EVENT_TYPE,     nullSafe(metadata.getEventType()));

        log.debug("Processing event type={} version={} producer={} correlationId={}",
            metadata.getEventType(),
            metadata.getEventVersion(),
            metadata.getProducerService(),
            metadata.getCorrelationId());
    }

    /**
     * Clear MDC after processing to prevent context leaking
     * into the next message on the same thread.
     */
    protected void clearContext() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_EVENT_ID);
        MDC.remove(MDC_EVENT_TYPE);
    }

    /**
     * Guard against eventVersion mismatches.
     * If the event version is higher than what this consumer supports,
     * throw to trigger the retry/DLQ path rather than processing silently.
     */
    protected void assertVersion(PulsePayEventMetadata metadata, int maxSupportedVersion) {
        if (metadata.getEventVersion() > maxSupportedVersion) {
            throw new UnsupportedEventVersionException(
                String.format(
                    "Consumer does not support eventVersion=%d for eventType=%s. " +
                    "Max supported version=%d. Routing to DLQ.",
                    metadata.getEventVersion(),
                    metadata.getEventType(),
                    maxSupportedVersion
                )
            );
        }
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "unknown";
    }

    // ── Inner exception — triggers DLQ routing in DefaultErrorHandler ──

    public static class UnsupportedEventVersionException extends RuntimeException {
        public UnsupportedEventVersionException(String message) {
            super(message);
        }
    }
}
