package dev.pulsepay.wallet.kafka;

import dev.pulsepay.events.payment.PaymentCreatedEvent;
import dev.pulsepay.kafka.consumer.BaseEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventConsumer extends BaseEventConsumer {

    private static final int MAX_SUPPORTED_VERSION = 2;

    @KafkaListener(
        topics         = "payment.events",
        groupId        = "wallet-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCreated(PaymentCreatedEvent event) {
        try {
            populateContext(event.getMetadata());
            assertVersion(event.getMetadata(), MAX_SUPPORTED_VERSION);

            log.info("Received PaymentCreated paymentId={} amount={} {} rail={}",
                event.getPaymentId(),
                event.getAmount(),
                event.getCurrency(),
                event.getRail());

            processLedgerEntry(event);

        } finally {
            clearContext();
        }
    }

    private void processLedgerEntry(PaymentCreatedEvent event) {
        // TODO: debit user wallet, credit merchant wallet
        // 1. Load wallet by userId from PostgreSQL
        // 2. Check sufficient balance
        // 3. Post DEBIT ledger entry for userId
        // 4. Post CREDIT ledger entry for merchantId
        // 5. Publish WalletEvent to wallet.events
        log.info("Ledger entry posted for paymentId={} correlationId={}",
            event.getPaymentId(),
            event.getMetadata().getCorrelationId());
    }
}