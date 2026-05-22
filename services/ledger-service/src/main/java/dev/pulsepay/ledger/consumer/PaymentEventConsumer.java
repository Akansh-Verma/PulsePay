package dev.pulsepay.ledger.consumer;

import dev.pulsepay.ledger.event.PaymentCreatedEvent;
import dev.pulsepay.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LedgerService ledgerService;

    @KafkaListener(
            topics = "payment.events",
            groupId = "ledger-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentCreated(
            @Payload PaymentCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        String correlationId = event != null && event.getCorrelationId() != null
                ? event.getCorrelationId()
                : "unknown";

        MDC.put("correlationId", correlationId);
        try {
            log.info(
                    "event=payment_event_received partition={} offset={} paymentId={} userId={} amount={} currency={} status={} correlationId={}",
                    partition,
                    offset,
                    event != null ? event.getPaymentId() : null,
                    event != null ? event.getUserId() : null,
                    event != null ? event.getAmount() : null,
                    event != null ? event.getCurrency() : null,
                    event != null ? event.getStatus() : null,
                    correlationId);

            ledgerService.processPaymentEvent(event);

            log.info("event=payment_event_processed_ok paymentId={} correlationId={}",
                    event != null ? event.getPaymentId() : null,
                    correlationId);
        } catch (Exception e) {
            log.error(
                    "event=payment_event_processing_failed partition={} offset={} correlationId={} error={}",
                    partition,
                    offset,
                    correlationId,
                    e.getMessage(),
                    e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
