package dev.pulsepay.ledger.service;

import dev.pulsepay.ledger.event.PaymentCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LedgerService {

    public void processPaymentEvent(PaymentCreatedEvent event) {
        if (event == null || event.getPaymentId() == null) {
            log.warn("event=ledger_skip_invalid_payload reason=missing_payment_id");
            return;
        }

        String correlationId = event.getCorrelationId() != null ? event.getCorrelationId() : "unknown";

        log.info(
                "event=ledger_simulated_write paymentId={} userId={} amount={} currency={} status={} correlationId={}",
                event.getPaymentId(),
                event.getUserId(),
                event.getAmount(),
                event.getCurrency(),
                event.getStatus(),
                correlationId);

        simulateLedgerLatency();
    }

    private void simulateLedgerLatency() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ledger simulation interrupted", e);
        }
    }
}
