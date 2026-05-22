package com.pulsepay.payment.controller;

import com.pulsepay.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<Map<String, Object>> initiatePayment(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId) {

        Map<String, Object> response = paymentService.initiatePayment(request, idempotencyKey, correlationId);
        return ResponseEntity.ok(response);
    }
}