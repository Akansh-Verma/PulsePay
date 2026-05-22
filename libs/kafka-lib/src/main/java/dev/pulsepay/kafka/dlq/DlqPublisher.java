package dev.pulsepay.kafka.dlq;

import dev.pulsepay.events.dlq.DeadLetterEvent;
import dev.pulsepay.events.dlq.DlqFailureType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Manually publish a failed message to its DLQ topic.
     * Spring's DefaultErrorHandler does this automatically for most failures,
     * but this method is available for cases where you want explicit control —
     * e.g. a business rule rejection that should not be retried.
     *
     * @param record      the original ConsumerRecord that failed
     * @param ex          the exception that caused the failure
     * @param failureType machine-readable classification
     * @param service     name of the service publishing to DLQ
     */
    public void publish(
            ConsumerRecord<String, Object> record,
            Exception ex,
            DlqFailureType failureType,
            String service) {

        String dlqTopic = record.topic() + ".dlq";

        byte[] rawBytes = extractRawBytes(record);

        DeadLetterEvent dlqEvent = DeadLetterEvent.newBuilder()
            .setDlqEventId(UUID.randomUUID().toString())
            .setOriginalTopic(record.topic())
            .setOriginalPartition(record.partition())
            .setOriginalOffset(record.offset())
            .setConsumerGroup("unknown")
            .setConsumerService(service)
            .setFailureType(failureType)
            .setFailureReason(ex.getClass().getSimpleName() + ": " + ex.getMessage())
            .setFailedAt(Instant.now().toString())
            .setAttemptCount(1)
            .setOriginalPayload(ByteBuffer.wrap(rawBytes))
            .setOriginalEventId(null)
            .setOriginalEventType(null)
            .setOriginalEventVersion(null)
            .setTraceId(null)
            .setValidationErrors(null)
            .setRawHeaders(null)
            .build();

        log.warn("Publishing to DLQ topic={} originalTopic={} reason={}",
            dlqTopic, record.topic(), ex.getMessage());

        kafkaTemplate.send(dlqTopic, record.key(), dlqEvent);
    }

    private byte[] extractRawBytes(ConsumerRecord<String, Object> record) {
        try {
            if (record.value() instanceof byte[]) {
                return (byte[]) record.value();
            }
            return record.value() != null
                ? record.value().toString().getBytes()
                : new byte[0];
        } catch (Exception e) {
            log.warn("Could not extract raw bytes from failed record: {}", e.getMessage());
            return new byte[0];
        }
    }
}
