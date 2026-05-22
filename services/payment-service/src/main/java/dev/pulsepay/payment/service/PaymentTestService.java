package dev.pulsepay.payment.service;

import dev.pulsepay.payment.event.PaymentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTestService {

    private static final String KAFKA_TOPIC = "payment.events";

    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    public PaymentCreatedEvent publishTestPaymentEvent(String correlationId) {
        PaymentCreatedEvent event = PaymentCreatedEvent.createTestEvent(correlationId);

        try {
            kafkaTemplate.send(KAFKA_TOPIC, event.getPaymentId(), event).get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing payment event", e);
        } catch (ExecutionException e) {
            log.error("event=kafka_publish_failed correlationId={} paymentId={}",
                    correlationId, event.getPaymentId(), e.getCause());
            throw new IllegalStateException("Failed to publish payment event", e.getCause());
        } catch (TimeoutException e) {
            log.error("event=kafka_publish_timeout correlationId={} paymentId={}",
                    correlationId, event.getPaymentId(), e);
            throw new IllegalStateException("Timed out publishing payment event", e);
        }

        log.info(
                "event=payment_created_published topic={} paymentId={} userId={} amount={} currency={} status={} correlationId={}",
                KAFKA_TOPIC,
                event.getPaymentId(),
                event.getUserId(),
                event.getAmount(),
                event.getCurrency(),
                event.getStatus(),
                MDC.get("correlationId"));

        return event;
    }
}
