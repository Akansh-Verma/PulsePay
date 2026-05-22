package com.pulsepay.payment.service;

import dev.pulsepay.events.payment.PaymentCreatedEvent;
import dev.pulsepay.events.PulsePayEventMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    // private final BaseEventProducer eventProducer;

    public Map<String, Object> initiatePayment(Map<String, Object> request, String idempotencyKey, String correlationId) {
        String userId = (String) request.get("userId");
        String merchantId = (String) request.get("merchantId");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String currency = (String) request.get("currency");
        String rail = (String) request.get("rail");

        String paymentId = UUID.randomUUID().toString();

        // TODO: Create and publish PaymentCreatedEvent

        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", paymentId);
        response.put("status", "INITIATED");
        response.put("message", "Payment initiated successfully");

        log.info("Payment initiated paymentId={} userId={} amount={}", paymentId, userId, amount);

        return response;
    }
}