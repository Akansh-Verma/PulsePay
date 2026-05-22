package com.pulsepay.fraud.service;

import com.pulsepay.fraud.config.AppConfig;
import dev.pulsepay.events.fraud.FraudAlertEvent;
import dev.pulsepay.events.fraud.FraudDecision;
import dev.pulsepay.events.fraud.FraudSeverity;
import dev.pulsepay.events.fraud.RuleHit;
import dev.pulsepay.events.payment.PaymentCreatedEvent;
import dev.pulsepay.kafka.consumer.BaseEventConsumer;
import dev.pulsepay.kafka.producer.BaseEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionConsumer extends BaseEventConsumer {

    private final AppConfig appConfig;
    private final BaseEventProducer eventProducer;

    @KafkaListener(topics = "payment.events", groupId = "fraud-group")
    public void consumePaymentEvent(ConsumerRecord<String, PaymentCreatedEvent> record) {
        PaymentCreatedEvent event = record.value();

        try {
            populateContext(event.getMetadata());

            log.info("Received payment event: paymentId={}, userId={}, amount={}, currency={}",
                event.getPaymentId(), event.getUserId(), event.getAmount(), event.getCurrency());

            // Simple fraud detection rule: amount > threshold
            if (isFraudulent(event)) {
                log.warn("Fraud detected for payment: paymentId={}, amount={}",
                    event.getPaymentId(), event.getAmount());

                // Publish fraud alert
                publishFraudAlert(event);
            } else {
                log.debug("Payment passed fraud check: paymentId={}", event.getPaymentId());
            }

        } catch (Exception e) {
            log.error("Error processing payment event: paymentId={}", event.getPaymentId(), e);
            throw e; // Let Spring Kafka handle retry/DLQ
        } finally {
            clearContext();
        }
    }

    private boolean isFraudulent(PaymentCreatedEvent event) {
        // Simple rule: amount > threshold
        return event.getAmount() > appConfig.getFraudThreshold();
    }

    private void publishFraudAlert(PaymentCreatedEvent paymentEvent) {
        try {
            String alertId = UUID.randomUUID().toString();

            RuleHit ruleHit = RuleHit.newBuilder()
                .setRuleCode("HIGH_AMOUNT")
                .setDescription("Payment amount exceeds fraud threshold")
                .setWeight(1.0)
                .build();

            FraudAlertEvent alertEvent = FraudAlertEvent.newBuilder()
                .setMetadata(eventProducer.buildMetadata(
                    "FraudAlert",
                    1,
                    "fraud-service",
                    paymentEvent.getMetadata().getTraceId(),
                    paymentEvent.getMetadata().getCorrelationId()
                ))
                .setAlertId(alertId)
                .setPaymentId(paymentEvent.getPaymentId())
                .setUserId(paymentEvent.getUserId())
                .setSeverity(FraudSeverity.HIGH)
                .setDecision(FraudDecision.REVIEW)
                .setRiskScore(0.8)
                .setTriggeredAt(Instant.now().toString())
                .setRuleHits(List.of(ruleHit))
                .setAccountId(null)
                .build();

            eventProducer.publish("fraud.alerts", paymentEvent.getPaymentId(), alertEvent);

            log.info("Published fraud alert: alertId={}, paymentId={}", alertId, paymentEvent.getPaymentId());

        } catch (Exception e) {
            log.error("Failed to publish fraud alert for payment: {}", paymentEvent.getPaymentId(), e);
        }
    }
}