package dev.pulsepay.ledger.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JSON payload for {@code payment.events}, aligned with payment-service producer shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
}
