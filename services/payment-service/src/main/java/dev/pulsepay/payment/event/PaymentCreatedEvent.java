package dev.pulsepay.payment.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal PaymentCreatedEvent for Kafka publishing.
 * Contains only essential fields for payment event tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreatedEvent {

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("amount")
    private double amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("correlationId")
    private String correlationId;

    /**
     * Factory method to create a dummy test event.
     */
    public static PaymentCreatedEvent createTestEvent(String correlationId) {
        return PaymentCreatedEvent.builder()
            .paymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .userId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .amount(99.99)
            .currency("USD")
            .status("INITIATED")
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
    }
}
