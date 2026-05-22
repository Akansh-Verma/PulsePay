package dev.pulsepay.kafka.producer;

import dev.pulsepay.events.PulsePayEventMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BaseEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish any Avro SpecificRecord to a Kafka topic.
     *
     * @param topic   target topic name e.g. "payment.events"
     * @param key     message key — use paymentId or userId for partition locality
     * @param event   Avro generated event object
     */
    public CompletableFuture<SendResult<String, Object>> publish(
            String topic, String key, SpecificRecord event) {

        log.info("Publishing event type={} to topic={} key={}",
            event.getClass().getSimpleName(), topic, key);

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event type={} topic={} key={} error={}",
                    event.getClass().getSimpleName(), topic, key, ex.getMessage());
            } else {
                log.debug("Published event type={} topic={} partition={} offset={}",
                    event.getClass().getSimpleName(),
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    /**
     * Build the shared metadata envelope that every event carries.
     * Called by each service before constructing its domain event.
     *
     * @param eventType       canonical name e.g. "PaymentCreated"
     * @param eventVersion    schema version integer — must match CHANGELOG.md
     * @param producerService name of the calling service e.g. "payment-service"
     * @param traceId         distributed trace ID from the incoming HTTP request
     * @param correlationId   workflow correlation ID — flows across all services
     */
    public PulsePayEventMetadata buildMetadata(
            String eventType,
            int eventVersion,
            String producerService,
            String traceId,
            String correlationId) {

        return PulsePayEventMetadata.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setEventType(eventType)
            .setEventVersion(eventVersion)
            .setOccurredAt(Instant.now().toString())
            .setTraceId(traceId)
            .setCorrelationId(correlationId)
            .setProducerService(producerService)
            .setEnvironment(null)
            .setSchemaId(null)
            .setTenantId(null)
            .build();
    }
}
