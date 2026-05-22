# PulsePay Code Reading Order - "Must Read First"

## Read These Files In This Order (15 minutes total)

### 1. [services/payment-service/src/main/java/dev/pulsepay/payment/controller/PaymentTestController.java](services/payment-service/src/main/java/dev/pulsepay/payment/controller/PaymentTestController.java)

**Why**: Entry point for HTTP request. Shows where `/payments/test` endpoint lives.

**Key Method**: `testPaymentEvent()`
```java
@PostMapping("/test")
public ResponseEntity<PaymentCreatedEvent> testPaymentEvent() {
    String correlationId = MDC.get("correlationId");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(paymentTestService.publishTestPaymentEvent(correlationId));
}
```

**What to understand**:
- Route is `/payments/test`
- Extracts `correlationId` from MDC (set by filter)
- Calls service to publish event
- Returns HTTP 201 with event payload

**Time**: 2 minutes

---

### 2. [services/payment-service/src/main/java/dev/pulsepay/payment/filter/CorrelationIdFilter.java](services/payment-service/src/main/java/dev/pulsepay/payment/filter/CorrelationIdFilter.java)

**Why**: Explains how correlationId gets into MDC (the thread-local context)

**Key Method**: `doFilter()`
```java
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    String correlationId = getCorrelationId(request);
    MDC.put("correlationId", correlationId);
    try {
        chain.doFilter(request, response);
    } finally {
        MDC.clear();
    }
}
```

**What to understand**:
- Runs BEFORE controller
- Reads `X-Correlation-Id` header from HTTP request (or generates UUID)
- Puts it in MDC so all logs include it
- Cleans up MDC after request finishes

**Time**: 2 minutes

---

### 3. [services/payment-service/src/main/java/dev/pulsepay/payment/service/PaymentTestService.java](services/payment-service/src/main/java/dev/pulsepay/payment/service/PaymentTestService.java)

**Why**: Shows how event is created and published to Kafka

**Key Method**: `publishTestPaymentEvent()`
```java
public PaymentCreatedEvent publishTestPaymentEvent(String correlationId) {
    PaymentCreatedEvent event = PaymentCreatedEvent.createTestEvent(correlationId);
    
    try {
        SendResult<String, PaymentCreatedEvent> result = kafkaTemplate.send(
            "payment.events",
            event.getPaymentId(),
            event
        ).get(30, TimeUnit.SECONDS);
        
        log.info("Payment event published to Kafka");
        return event;
    } catch (TimeoutException e) {
        log.error("Kafka publish timeout");
        throw new RuntimeException(e);
    }
}
```

**What to understand**:
- Creates event with `PaymentCreatedEvent.createTestEvent()`
- Calls `kafkaTemplate.send()` to publish to Kafka
- Key = `paymentId`, Value = full event object
- Waits up to 30 seconds for Kafka acknowledgment
- If timeout, throws exception

**Time**: 3 minutes

---

### 4. [services/payment-service/src/main/java/dev/pulsepay/payment/event/PaymentCreatedEvent.java](services/payment-service/src/main/java/dev/pulsepay/payment/event/PaymentCreatedEvent.java)

**Why**: Shows the exact event structure that flows through system

**Key Class**:
```java
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCreatedEvent {
    private String paymentId;           // "pay_abc123..."
    private String userId;              // "user_xyz789..."
    private double amount;              // 99.99
    private String currency;            // "USD"
    private String status;              // "INITIATED"
    private Instant timestamp;          // ISO-8601
    private String correlationId;       // Passed through
    
    public static PaymentCreatedEvent createTestEvent(String correlationId) {
        return PaymentCreatedEvent.builder()
            .paymentId("pay_" + generateId())
            .userId("user_" + generateId())
            .amount(99.99)
            .currency("USD")
            .status("INITIATED")
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
    }
}
```

**What to understand**:
- This is the message format that goes to Kafka
- 7 fields total
- Gets serialized to JSON for Kafka
- Gets deserialized by ledger-service consumer

**Time**: 2 minutes

---

### 5. [services/payment-service/src/main/java/dev/pulsepay/payment/config/KafkaConfig.java](services/payment-service/src/main/java/dev/pulsepay/payment/config/KafkaConfig.java)

**Why**: Shows how Kafka producer is configured

**Key Bean**: `kafkaTemplate()`
```java
@Bean
public KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate(
    ProducerFactory<String, PaymentCreatedEvent> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
}

@Bean
public ProducerFactory<String, PaymentCreatedEvent> producerFactory() {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ACKS_CONFIG, "all",                    // Wait for all replicas
            RETRIES_CONFIG, 3,
            KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        )
    );
}
```

**What to understand**:
- Creates KafkaTemplate bean (used by service)
- Keys are strings (paymentId)
- Values are JSON (PaymentCreatedEvent)
- `acks: all` means Kafka waits for all replicas before acknowledging
- 3 retries on failure

**Time**: 2 minutes

---

### 6. [services/ledger-service/src/main/java/dev/pulsepay/ledger/consumer/PaymentEventConsumer.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/consumer/PaymentEventConsumer.java)

**Why**: Shows how ledger-service listens for and receives messages from Kafka

**Key Method**: `onPaymentCreatedEvent()`
```java
@KafkaListener(
    topics = "payment.events",
    groupId = "ledger-group"
)
public void onPaymentCreatedEvent(
    PaymentCreatedEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
    @Header(KafkaHeaders.OFFSET) long offset) {
    
    String correlationId = event.getCorrelationId();
    MDC.put("correlationId", correlationId);
    
    try {
        log.info("event=payment_event_received paymentId={}", event.getPaymentId());
        ledgerService.processPaymentEvent(event);
    } finally {
        MDC.clear();
    }
}
```

**What to understand**:
- `@KafkaListener` annotation marks this as consumer
- Listens to topic `payment.events`
- Consumer group `ledger-group` (ensures only one instance processes each message)
- Automatically deserializes JSON to PaymentCreatedEvent
- Extracts `correlationId` and sets in MDC (same as producer!)
- Calls `LedgerService.processPaymentEvent()`

**Time**: 3 minutes

---

### 7. [services/ledger-service/src/main/java/dev/pulsepay/ledger/service/LedgerService.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/service/LedgerService.java)

**Why**: Shows what ledger-service actually does with the event

**Key Method**: `processPaymentEvent()`
```java
public void processPaymentEvent(PaymentCreatedEvent event) {
    String paymentId = event.getPaymentId();
    
    if (paymentId == null || paymentId.isEmpty()) {
        log.error("Invalid paymentId");
        return;
    }
    
    simulateLedgerLatency();
    
    log.info("event=ledger_simulated_write paymentId={} amount={} currency={}",
        paymentId, event.getAmount(), event.getCurrency());
}

private void simulateLedgerLatency() {
    try {
        Thread.sleep(25);  // Simulate database write time
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

**What to understand**:
- Receives event from consumer
- Validates paymentId
- Simulates latency (25ms sleep) like a real database write
- Logs the event (in real system, would INSERT into database)
- No return value - just processes

**Time**: 2 minutes

---

### 8. [services/ledger-service/src/main/java/dev/pulsepay/ledger/config/KafkaConfig.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/config/KafkaConfig.java)

**Why**: Shows how Kafka consumer is configured

**Key Bean**: `consumerFactory()`
```java
@Bean
public ConsumerFactory<String, PaymentCreatedEvent> consumerFactory() {
    return new DefaultKafkaConsumerFactory<>(
        Map.of(
            BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            GROUP_ID_CONFIG, "ledger-group",
            AUTO_OFFSET_RESET_CONFIG, "earliest",  // Start from beginning
            KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
            VALUE_DEFAULT_TYPE, "dev.pulsepay.ledger.event.PaymentCreatedEvent"
        )
    );
}
```

**What to understand**:
- Deserializes keys as strings (paymentId)
- Deserializes values as JSON → PaymentCreatedEvent
- Consumer group is `ledger-group`
- `auto-offset-reset: earliest` means if no saved offset, start from beginning
- Auto-commit enabled (message marked as processed after handler completes)

**Time**: 2 minutes

---

## Visual Reading Flow

```
START HERE
    │
    ▼
[1] PaymentTestController.testPaymentEvent()
    ├─ "Where does /payments/test route?"
    │
    ▼
[2] CorrelationIdFilter.doFilter()
    ├─ "Where does correlationId come from?"
    │
    ▼
[3] PaymentTestService.publishTestPaymentEvent()
    ├─ "How is event created and published?"
    │
    ▼
[4] PaymentCreatedEvent
    ├─ "What's in the event message?"
    │
    ▼
[5] PaymentService KafkaConfig
    ├─ "How is Kafka producer configured?"
    │
    ▼
         [KAFKA TOPIC: payment.events]
    ├─ Message stored, awaiting consumer
    │
    ▼
[6] PaymentEventConsumer.onPaymentCreatedEvent()
    ├─ "How does ledger-service receive it?"
    │
    ▼
[7] LedgerService.processPaymentEvent()
    ├─ "What happens when ledger processes it?"
    │
    ▼
[8] LedgerService KafkaConfig
    ├─ "How is Kafka consumer configured?"
    │
    ▼
   DONE ✓
```

---

## Method Call Stack (For Debugging)

```
POST /payments/test (HTTP)
  │
  ├─→ CorrelationIdFilter.doFilter()
  │   └─→ MDC.put("correlationId", "...")
  │
  ├─→ PaymentTestController.testPaymentEvent()
  │   └─→ MDC.get("correlationId")
  │   └─→ PaymentTestService.publishTestPaymentEvent(correlationId)
  │
  ├─→ PaymentTestService.publishTestPaymentEvent()
  │   ├─→ PaymentCreatedEvent.createTestEvent(correlationId)
  │   │   └─→ Returns new event object
  │   └─→ kafkaTemplate.send(topic, key, event)
  │       └─→ Waits 30s for Kafka ack
  │
  ├─→ [HTTP 201 Response sent to client]
  │
  ├─→ [Kafka stores message in topic]
  │
  ├─→ PaymentEventConsumer.onPaymentCreatedEvent(event)  [In ledger-service]
  │   ├─→ MDC.put("correlationId", event.getCorrelationId())
  │   └─→ LedgerService.processPaymentEvent(event)
  │
  └─→ LedgerService.processPaymentEvent()
      ├─→ Validate paymentId
      ├─→ simulateLedgerLatency() [Thread.sleep(25ms)]
      └─→ Log event details
```

---

## What Each Service Is Responsible For

| Aspect | Payment-Service | Kafka | Ledger-Service |
|--------|-----------------|-------|-----------------|
| **Listens On** | Port 8082 | N/A | Port 8084 |
| **HTTP API** | ✓ YES (/payments/test) | ✗ NO | ✗ NO |
| **Creates Events** | ✓ YES | ✗ NO | ✗ NO |
| **Publishes to Kafka** | ✓ YES | N/A | ✗ NO |
| **Receives from Kafka** | ✗ NO | ✓ YES (routes) | ✓ YES |
| **Processes Events** | ✗ NO | ✗ NO | ✓ YES |
| **Stores in Database** | ✗ NO (test) | ✗ NO | ✗ NO (test, just logs) |

---

## Core Concepts Summary

### Concepts from Each File

**CorrelationIdFilter**
- Concept: **MDC (Mapped Diagnostic Context)** - thread-local storage for logging context
- Benefit: Every log line includes correlationId automatically

**PaymentTestController**
- Concept: **Spring @Controller** and **@PostMapping** - HTTP endpoint routing
- Benefit: Clean separation of HTTP concerns from business logic

**PaymentTestService**
- Concept: **KafkaTemplate** - Spring abstraction over Kafka producer
- Benefit: Simpler API than raw Kafka producer, handles serialization

**PaymentCreatedEvent**
- Concept: **DTO (Data Transfer Object)** - message structure
- Benefit: Type-safe, JSON serializable, shared between services

**PaymentService KafkaConfig**
- Concept: **ProducerFactory** - Kafka producer configuration
- Benefit: Centralized config, easy to change serialization/reliability settings

**PaymentEventConsumer**
- Concept: **@KafkaListener** - Spring annotation for async message consumption
- Benefit: Decouples request/response, services run independently

**LedgerService**
- Concept: **Async processing** - handler completes, next message arrives
- Benefit: High throughput, failures don't block other messages

**LedgerService KafkaConfig**
- Concept: **ConsumerFactory** - Kafka consumer configuration
- Benefit: Handles deserialization, group management, offset commits

---

## Typical Issues & Where to Check

| Issue | Check File | Method |
|-------|-----------|--------|
| correlationId not in logs | CorrelationIdFilter | doFilter() |
| HTTP endpoint returns 500 | PaymentTestController | testPaymentEvent() |
| Event not published to Kafka | PaymentTestService | publishTestPaymentEvent() |
| Event structure wrong | PaymentCreatedEvent | Getter fields |
| Messages not deserialized | LedgerService KafkaConfig | consumerFactory() |
| Consumer not receiving | PaymentEventConsumer | @KafkaListener |
| Event not processed | LedgerService | processPaymentEvent() |

---

## Quick Test: Can You Explain...

After reading these 8 files, you should be able to explain:

1. ✓ "Where does correlationId come from?" → CorrelationIdFilter
2. ✓ "What HTTP route is exposed?" → PaymentTestController
3. ✓ "How is the event created?" → PaymentTestService + PaymentCreatedEvent
4. ✓ "What fields are in the event?" → PaymentCreatedEvent
5. ✓ "How does it get to Kafka?" → KafkaTemplate.send()
6. ✓ "What topic and key?" → "payment.events" and paymentId
7. ✓ "How does ledger-service know?" → @KafkaListener
8. ✓ "What happens when it arrives?" → LedgerService.processPaymentEvent()
9. ✓ "Why 25ms sleep?" → Simulate database latency
10. ✓ "What's the timeline?" → ~50ms total

If you can answer all 10, you understand the system! ✓

