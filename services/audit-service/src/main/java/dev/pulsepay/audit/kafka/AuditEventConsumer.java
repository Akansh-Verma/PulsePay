package dev.pulsepay.audit.kafka;

import dev.pulsepay.events.payment.PaymentCreatedEvent;
import dev.pulsepay.events.fraud.FraudAlertEvent;
import dev.pulsepay.kafka.consumer.BaseEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditEventConsumer extends BaseEventConsumer {

    private static final int MAX_SUPPORTED_VERSION = 2;

    @KafkaListener(
        topics           = "payment.events",
        groupId          = "audit-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCreated(PaymentCreatedEvent event) {
        try {
            populateContext(event.getMetadata());
            assertVersion(event.getMetadata(), MAX_SUPPORTED_VERSION);

            // TODO: write AuditRecord to MongoDB
            // fields: entityType=Payment, entityId=paymentId,
            //         action=PAYMENT_INITIATED, actorType=USER,
            //         actorId=userId, result=SUCCESS,
            //         ipAddress + sessionId from event if present
            log.info("Audit record written paymentId={} userId={} correlationId={}",
                event.getPaymentId(),
                event.getUserId(),
                event.getMetadata().getCorrelationId());

        } finally {
            clearContext();
        }
    }

    @KafkaListener(
        topics           = "fraud.alerts",
        groupId          = "audit-service-fraud-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFraudAlert(FraudAlertEvent event) {
        try {
            populateContext(event.getMetadata());
            assertVersion(event.getMetadata(), MAX_SUPPORTED_VERSION);

            // TODO: write AuditRecord to MongoDB
            // fields: entityType=FraudAlert, entityId=alertId,
            //         action=FRAUD_ALERT_RAISED, result=SUCCESS
            log.warn("Audit record written for fraud alert alertId={} decision={} correlationId={}",
                event.getAlertId(),
                event.getDecision(),
                event.getMetadata().getCorrelationId());

        } finally {
            clearContext();
        }
    }
}