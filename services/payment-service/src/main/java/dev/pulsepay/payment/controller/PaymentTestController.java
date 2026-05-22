package dev.pulsepay.payment.controller;

import dev.pulsepay.payment.event.PaymentCreatedEvent;
import dev.pulsepay.payment.service.PaymentTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /payments/test — publishes a dummy {@link PaymentCreatedEvent} to Kafka.
 * Correlation ID is set by {@link dev.pulsepay.payment.filter.CorrelationIdFilter} in MDC.
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentTestController {

    private final PaymentTestService paymentTestService;

    @PostMapping("/test")
    public ResponseEntity<PaymentCreatedEvent> testPaymentEvent() {
        String correlationId = MDC.get("correlationId");
        log.info("event=test_payment_request correlationId={}", correlationId);

        PaymentCreatedEvent event = paymentTestService.publishTestPaymentEvent(correlationId);

        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }
}
