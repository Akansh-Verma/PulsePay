package dev.pulsepay.fraud.kafka;

import dev.pulsepay.events.fraud.FraudAlertEvent;
import dev.pulsepay.events.fraud.FraudDecision;
import dev.pulsepay.events.fraud.FraudSeverity;
import dev.pulsepay.events.fraud.RuleHit;
import dev.pulsepay.events.payment.PaymentCreatedEvent;
import dev.pulsepay.kafka.consumer.BaseEventConsumer;
import dev.pulsepay.kafka.producer.BaseEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer extends BaseEventConsumer {

    private static final int    MAX_SUPPORTED_VERSION  = 2;
    private static final String TOPIC                  = "fraud.alerts";
    private static final String SERVICE_NAME           = "fraud-detection";
    private static final int    VELOCITY_LIMIT         = 5;
    private static final Duration VELOCITY_WINDOW      = Duration.ofMinutes(10);

    private final BaseEventProducer  baseProducer;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
        topics           = "payment.events",
        groupId          = "fraud-detection-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCreated(PaymentCreatedEvent event) {
        try {
            populateContext(event.getMetadata());
            assertVersion(event.getMetadata(), MAX_SUPPORTED_VERSION);

            log.info("Fraud check paymentId={} userId={} amount={}",
                event.getPaymentId(), event.getUserId(), event.getAmount());

            evaluateRules(event);

        } finally {
            clearContext();
        }
    }

    private void evaluateRules(PaymentCreatedEvent event) {
        String velocityKey = "velocity:" + event.getUserId();

        // Increment velocity counter — how many payments in last 10 minutes
        Long count = redisTemplate.opsForValue().increment(velocityKey);
        if (count == 1) {
            redisTemplate.expire(velocityKey, VELOCITY_WINDOW);
        }

        boolean velocityBreached = count > VELOCITY_LIMIT;
        boolean highValue        = event.getAmount() > 50000;

        if (!velocityBreached && !highValue) {
            log.debug("Fraud check PASS paymentId={}", event.getPaymentId());
            return;
        }

        // Build rule hits for the alert
        List<RuleHit> ruleHits = new java.util.ArrayList<>();

        if (velocityBreached) {
            ruleHits.add(RuleHit.newBuilder()
                .setRuleCode("VELOCITY_EXCEEDED")
                .setDescription("User exceeded " + VELOCITY_LIMIT + " payments in 10 minutes")
                .setWeight(0.7)
                .build());
        }
        if (highValue) {
            ruleHits.add(RuleHit.newBuilder()
                .setRuleCode("HIGH_VALUE_TRANSACTION")
                .setDescription("Transaction amount exceeds 50,000")
                .setWeight(0.5)
                .build());
        }

        double riskScore  = ruleHits.stream().mapToDouble(RuleHit::getWeight).sum();
        riskScore         = Math.min(riskScore, 1.0);
        FraudDecision decision = riskScore >= 0.7 ? FraudDecision.BLOCK : FraudDecision.REVIEW;

        var metadata = baseProducer.buildMetadata(
            "FraudAlert", 2, SERVICE_NAME,
            event.getMetadata().getTraceId().toString(),
            event.getMetadata().getCorrelationId() != null
                ? event.getMetadata().getCorrelationId().toString()
                : null);

        var alert = FraudAlertEvent.newBuilder()
            .setMetadata(metadata)
            .setAlertId(UUID.randomUUID().toString())
            .setPaymentId(event.getPaymentId())
            .setUserId(event.getUserId())
            .setSeverity(riskScore >= 0.7 ? FraudSeverity.HIGH : FraudSeverity.MEDIUM)
            .setDecision(decision)
            .setRiskScore(riskScore)
            .setTriggeredAt(Instant.now().toString())
            .setRuleHits(ruleHits)
            .setAccountId(null)
            .build();

        baseProducer.publish(TOPIC, event.getPaymentId(), alert);

        log.warn("Fraud alert published paymentId={} decision={} riskScore={}",
            event.getPaymentId(), decision, riskScore);
    }
}