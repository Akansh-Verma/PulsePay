# PulsePay POST /payments/test - Sequence Diagram

## Detailed Sequence Flow

```
┌────────┐          ┌──────────────────┐      ┌──────────────────┐      ┌──────┐      ┌────────────────┐
│ Client │          │ payment-service  │      │ Kafka Topic      │      │ Kafka│      │ ledger-service │
│        │          │                  │      │ payment.events   │      │ UI   │      │                │
└───┬────┘          └────────┬─────────┘      └──────────────────┘      └──────┘      └────────┬───────┘
    │                        │                                                              │
    │ POST /payments/test    │                                                              │
    │─────────────────────→  │                                                              │
    │                        │                                                              │
    │                ┌───────▼─────┐                                                        │
    │                │Filter        │                                                      │
    │                │MDC.set(      │                                                      │
    │                │  correlationId│                                                     │
    │                │)             │                                                      │
    │                └───────┬─────┘                                                        │
    │                        │                                                              │
    │                ┌───────▼──────────────┐                                               │
    │                │Controller.test()     │                                               │
    │                │- Get correlationId   │                                               │
    │                │  from MDC            │                                               │
    │                │- Call Service        │                                               │
    │                └───────┬──────────────┘                                               │
    │                        │                                                              │
    │                ┌───────▼──────────────────────────────┐                               │
    │                │Service.publish()                     │                               │
    │                │- Create PaymentCreatedEvent          │                               │
    │                │- Call kafkaTemplate.send(            │                               │
    │                │    topic="payment.events",           │                               │
    │                │    key=paymentId,                    │                               │
    │                │    value=event)                      │                               │
    │                │- Wait 30s for ack                    │                               │
    │                └───────┬──────────────────────────────┘                               │
    │                        │                                                              │
    │                        │ Serialize Event to JSON                                      │
    │                        │─────────────────────────────→│                               │
    │                        │                              │                               │
    │                        │                    ┌─────────▼────────┐                      │
    │                        │                    │ Store in Topic   │                      │
    │                        │                    │ partition 0-2    │                      │
    │                        │                    │ offset N         │                      │
    │                        │                    └─────────┬────────┘                      │
    │                        │                              │                               │
    │                        │           Kafka UI can view │                               │
    │                        │           here─────────────→│ View Messages                 │
    │                        │                              │──→ Show JSON                 │
    │                        │                              │                               │
    │  HTTP 201 + Event JSON │                              │                               │
    │ ◄────────────────────── │                              │                               │
    │                        │                              │                               │
    │                        │  Consumer subscribes (ledger-group)                          │
    │                        │─────────────────────────────────────→│                      │
    │                        │                              │      │                       │
    │                        │                              │      │ Deserialize JSON     │
    │                        │                              │      │ → PaymentCreatedEvent
    │                        │                              │      │◄─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
    │                        │                              │      │                       │
    │                        │                              │      │┌─────────────────────┤
    │                        │                              │      ││@KafkaListener       │
    │                        │                              │      ││onPaymentCreated()   │
    │                        │                              │      ││- Extract corr ID   │
    │                        │                              │      ││- Set MDC           │
    │                        │                              │      ││- Call LedgerService│
    │                        │                              │      │└────────┬───────────┤
    │                        │                              │      │         │           │
    │                        │                              │      │    ┌────▼────┐      │
    │                        │                              │      │    │Ledger   │      │
    │                        │                              │      │    │Service  │      │
    │                        │                              │      │    │Validate │      │
    │                        │                              │      │    │Sleep 25ms      │
    │                        │                              │      │    │Log write│      │
    │                        │                              │      │    └────┬────┘      │
    │                        │                              │      │         │           │
    │                        │                              │      │  ┌──────▼──────┐  │
    │                        │                              │      │  │ Auto-commit  │  │
    │                        │                              │      │  │ offset       │  │
    │                        │                              │      │  └──────────────┘  │
    │                        │                              │      │                    │
    │ [Done]                │ [Done]                       │      │ [Done]             │
    │                        │                              │      │                    │

Timeline:
T+0ms     Client sends POST
T+5ms     Filter populates MDC
T+10ms    Controller receives request
T+15ms    Event created, Kafka send() called
T+20ms    HTTP 201 returned to client ◄─── CLIENT SEES RESPONSE HERE
T+25ms    Ledger consumer receives message
T+50ms    Ledger processing complete (25ms sleep)
```

---

## Message Structure Throughout the Flow

### HTTP Request
```
POST /payments/test HTTP/1.1
Host: localhost:8082
X-Correlation-Id: debug-flow-001

(empty body)
```

### HTTP Response (201 Created)
```
HTTP/1.1 201 Created
Content-Type: application/json

{
  "paymentId": "pay_a1b2c3d4e5f6g7h8",
  "userId": "user_xyz123abc456",
  "amount": 99.99,
  "currency": "USD",
  "status": "INITIATED",
  "timestamp": "2026-05-22T10:15:30.123Z",
  "correlationId": "debug-flow-001"
}
```

### Kafka Message
```
Topic: payment.events
Partition: 0 (example, depends on key hash)
Offset: 42 (example, incremented per message)
Key: pay_a1b2c3d4e5f6g7h8
Value (JSON):
{
  "paymentId": "pay_a1b2c3d4e5f6g7h8",
  "userId": "user_xyz123abc456",
  "amount": 99.99,
  "currency": "USD",
  "status": "INITIATED",
  "timestamp": "2026-05-22T10:15:30.123Z",
  "correlationId": "debug-flow-001"
}
```

---

## Thread of Execution

```
PAYMENT-SERVICE THREAD:
  ├─ HttpThread: Request arrives
  ├─ FilterThread: CorrelationIdFilter.doFilter()
  ├─ DispatcherServlet: Routing
  ├─ ControllerThread: PaymentTestController.testPaymentEvent()
  ├─ ServiceThread: PaymentTestService.publishTestPaymentEvent()
  ├─ KafkaProducerThread: kafkaTemplate.send() → blocks for 30s
  │  └─ KafkaProducerCallback: Kafka ack received → unblocks
  ├─ ControllerThread: Returns response
  └─ HttpThread: Response sent to client
     [Client gets 201 response]

KAFKA (Async):
  ├─ ProducerThread: Receives message
  ├─ PartitionerThread: Determines partition (hash of key)
  └─ BrokerThread: Stores in log segment

LEDGER-SERVICE THREAD (Async):
  ├─ KafkaConsumerThread: Poll for messages
  ├─ ContainerThread: Create container
  ├─ ListenerThread: @KafkaListener method executes
  ├─ ServiceThread: LedgerService.processPaymentEvent()
  ├─ CommitterThread: Auto-commit offset
  └─ ListenerThread: Returns (ready for next message)
```

---

## MDC (Mapped Diagnostic Context) - Correlation Tracking

```
PAYMENT-SERVICE MDC Stack:
  correlationId=debug-flow-001     ← Set by CorrelationIdFilter
  traceId=uuid-12345               ← Set by CorrelationIdFilter
  
  All logs contain:
  [correlationId=debug-flow-001] message

LEDGER-SERVICE MDC Stack:
  correlationId=debug-flow-001     ← Extracted from PaymentCreatedEvent
  
  All logs contain:
  [correlationId=debug-flow-001] message
```

This allows you to grep/search for a single correlationId across both service logs and see the entire flow!

```bash
# Find all logs for one request:
docker logs payment-service | grep "debug-flow-001"
docker logs ledger-service | grep "debug-flow-001"
```

---

## Partition Assignment (Kafka Mechanics)

```
Topic: payment.events (3 partitions)

PaymentId: "pay_a1b2c3d4e5f6g7h8"
Hash: hash("pay_a1b2c3d4e5f6g7h8") = 12345 (example)
Partition: 12345 % 3 = 0

So this message goes to Partition 0.

Next request with different paymentId:
PaymentId: "pay_xyz789abcdef123"
Hash: hash("pay_xyz789abcdef123") = 54321
Partition: 54321 % 3 = 1

Messages with same paymentId always go to same partition,
ensuring order for that payment entity.
```

---

## Error Scenarios and Recovery

### Scenario 1: Kafka Broker Down
```
PAYMENT-SERVICE:
  ├─ kafkaTemplate.send() waits 30s for ack
  ├─ Timeout occurs
  ├─ KafkaException thrown
  ├─ Service catches exception
  └─ HTTP 500 returned (or 202 in graceful mode)

RECOVERY:
  └─ Restart Kafka broker
  └─ Retry the POST /payments/test request
```

### Scenario 2: Ledger-Service Down
```
KAFKA:
  ├─ Messages accumulate in topic payment.events
  ├─ Consumer group ledger-group shows LAG
  └─ Messages NOT consumed, NOT committed

RECOVERY:
  ├─ Restart ledger-service
  ├─ Consumer resumes from last committed offset
  ├─ All accumulated messages are processed
  └─ LAG reduces to 0
```

### Scenario 3: Deserialization Error in Ledger-Service
```
KAFKA MESSAGE:
  └─ Value malformed JSON (e.g., missing field)

LEDGER-SERVICE:
  ├─ JsonDeserializer fails
  ├─ ConsumerRecord not deserialized
  ├─ ErrorHandler invoked (default: log + skip)
  ├─ Offset committed anyway
  └─ Next message processed

RESULT: Message lost (not processed). Check logs for errors!
```

---

## Key Numbers (For Performance Tuning)

| Parameter | Value | Where Set |
|-----------|-------|-----------|
| Producer Ack Timeout | 30 seconds | PaymentTestService |
| Ledger Latency Simulation | 25 ms | LedgerService |
| Consumer Poll Timeout | (default) | KafkaConfig (ledger-service) |
| Partition Count | 3 | docker-compose.yml |
| Replication Factor | 1 | docker-compose.yml |
| Max In-Flight Requests | (default 5) | KafkaConfig (payment-service) |

