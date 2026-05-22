# PulsePay Runtime Flow Debugging Guide

**Goal**: Understand how `POST /payments/test` flows through payment-service → Kafka → ledger-service.

---

## Quick System Overview (Interview Language)

**payment-service** (Port 8082)
- Role: HTTP API that receives payment requests
- Does: Receives POST /payments/test → Creates a PaymentCreatedEvent → Publishes to Kafka topic `payment.events`
- Responsibility: Producer. Generates events, doesn't process them.

**Kafka** (Port 9092)
- Role: Distributed message broker
- Does: Receives events from producers, stores them in topics with partitions, sends them to subscribed consumers
- Responsibility: Reliable message delivery between services

**ledger-service** (Port 8084)
- Role: Listens for payment events from Kafka
- Does: Receives PaymentCreatedEvent from topic → Simulates ledger processing (logs it)
- Responsibility: Consumer. Processes events from Kafka, maintains ledger state (simulated).

---

## Step-by-Step Execution Flow

### Stage 1: HTTP Request → CorrelationIdFilter

```
Client: curl -X POST "http://localhost:8082/payments/test" \
            -H "X-Correlation-Id: demo-123"
            
↓ (CorrelationIdFilter intercepts ALL requests)

[CorrelationIdFilter.doFilter()]
├─ Extracts X-Correlation-Id from request header (or generates UUID)
├─ Sets MDC values:
│  ├─ correlationId = "demo-123" (or generated UUID)
│  └─ traceId = generated UUID
├─ Passes request to controller
└─ Cleans up MDC in finally block
```

**Key File**: [services/payment-service/src/main/java/dev/pulsepay/payment/filter/CorrelationIdFilter.java](services/payment-service/src/main/java/dev/pulsepay/payment/filter/CorrelationIdFilter.java)

**What logs appear**:
```
[payment-service logs] correlationId=demo-123 - Request intercepted by CorrelationIdFilter
```

---

### Stage 2: HTTP Request → Controller

```
[PaymentTestController.testPaymentEvent()]
├─ Route: POST /payments/test
├─ Extracts correlationId from MDC (set by filter)
│  └─ MDC.get("correlationId") = "demo-123"
├─ Calls paymentTestService.publishTestPaymentEvent(correlationId)
└─ Returns 201 CREATED + event payload as JSON
```

**Key File**: [services/payment-service/src/main/java/dev/pulsepay/payment/controller/PaymentTestController.java](services/payment-service/src/main/java/dev/pulsepay/payment/controller/PaymentTestController.java)

**What you see**:
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
  "correlationId": "demo-123"
}
```

---

### Stage 3: Service → Event Creation & Kafka Publishing

```
[PaymentTestService.publishTestPaymentEvent(correlationId)]
├─ Creates PaymentCreatedEvent using factory method:
│  │
│  └─ PaymentCreatedEvent.createTestEvent(correlationId)
│     ├─ paymentId = "pay_" + random hex (16 chars)
│     ├─ userId = "user_" + random hex (12 chars)
│     ├─ amount = 99.99
│     ├─ currency = "USD"
│     ├─ status = "INITIATED"
│     ├─ timestamp = Instant.now() (ISO-8601)
│     └─ correlationId = "demo-123" (passed through)
│
├─ Sends to Kafka using KafkaTemplate:
│  │
│  └─ kafkaTemplate.send(
│        topic: "payment.events",
│        key: paymentId,           // "pay_a1b2c3d4e5f6g7h8"
│        value: event              // Full PaymentCreatedEvent as JSON
│     )
│
├─ Waits for acknowledgment (30-second timeout)
│  ├─ If success: logs "Payment event published successfully"
│  ├─ If timeout: logs error + exception
│  └─ If interrupted: handles gracefully
│
└─ Returns event to controller → HTTP 201 response
```

**Key Files**:
- [services/payment-service/src/main/java/dev/pulsepay/payment/service/PaymentTestService.java](services/payment-service/src/main/java/dev/pulsepay/payment/service/PaymentTestService.java)
- [services/payment-service/src/main/java/dev/pulsepay/payment/event/PaymentCreatedEvent.java](services/payment-service/src/main/java/dev/pulsepay/payment/event/PaymentCreatedEvent.java)
- [services/payment-service/src/main/java/dev/pulsepay/payment/config/KafkaConfig.java](services/payment-service/src/main/java/dev/pulsepay/payment/config/KafkaConfig.java)

**What logs appear**:
```
[payment-service logs] correlationId=demo-123 event=payment_event_created paymentId=pay_a1b2c3d4e5f6g7h8
[payment-service logs] correlationId=demo-123 event=payment_event_published_to_kafka topic=payment.events
```

**JSON sent to Kafka**:
```json
{
  "paymentId": "pay_a1b2c3d4e5f6g7h8",
  "userId": "user_xyz123abc456",
  "amount": 99.99,
  "currency": "USD",
  "status": "INITIATED",
  "timestamp": "2026-05-22T10:15:30.123Z",
  "correlationId": "demo-123"
}
```

---

### Stage 4: Kafka Topic Storage

```
Kafka Topic: payment.events
├─ Partitions: 3
├─ Replication Factor: 1
└─ Message stored as:
   ├─ Key: "pay_a1b2c3d4e5f6g7h8" (paymentId)
   ├─ Value: [JSON serialized PaymentCreatedEvent]
   ├─ Offset: incremented
   └─ Timestamp: broker timestamp
```

**Kafka Bootstrap Servers**: `localhost:9092` (Docker) or `kafka:29092` (inside Docker network)

---

### Stage 5: Kafka Consumer → Ledger Service

```
[PaymentEventConsumer] (runs in ledger-service, container)
├─ Configuration:
│  ├─ Topic: payment.events
│  ├─ Consumer Group: ledger-group
│  ├─ Auto-offset-reset: earliest (start from beginning if no saved offset)
│  └─ Bootstrap Servers: kafka:29092 (Docker internal)
│
├─ Receives message from Kafka:
│  ├─ Partition: 0, 1, or 2 (depends on key hash)
│  ├─ Offset: auto-incremented
│  ├─ Key: "pay_a1b2c3d4e5f6g7h8"
│  └─ Value: JSON deserialized to PaymentCreatedEvent
│
├─ @KafkaListener method [onPaymentCreatedEvent]:
│  ├─ Extracts correlationId from event: "demo-123"
│  ├─ Sets MDC with correlationId (for logging)
│  ├─ Logs: "event=payment_event_received"
│  └─ Calls LedgerService.processPaymentEvent(event)
│
└─ Returns (auto-commit offset after successful processing)
```

**Key File**: [services/ledger-service/src/main/java/dev/pulsepay/ledger/consumer/PaymentEventConsumer.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/consumer/PaymentEventConsumer.java)

**What logs appear**:
```
[ledger-service logs] correlationId=demo-123 event=payment_event_received paymentId=pay_a1b2c3d4e5f6g7h8
```

---

### Stage 6: Ledger Service Processing

```
[LedgerService.processPaymentEvent(event)]
├─ Validation:
│  └─ Checks paymentId is not null/empty
│
├─ Simulated Latency:
│  └─ Thread.sleep(25 milliseconds)  // Simulate DB write time
│
├─ Simulated Ledger Write:
│  ├─ In real system: INSERT INTO ledger_entries (...)
│  ├─ In test system: Just log (no database)
│  └─ Logs: "event=ledger_simulated_write"
│
└─ Logs all event details with correlationId
```

**Key File**: [services/ledger-service/src/main/java/dev/pulsepay/ledger/service/LedgerService.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/service/LedgerService.java)

**What logs appear**:
```
[ledger-service logs] correlationId=demo-123 event=ledger_simulated_write paymentId=pay_a1b2c3d4e5f6g7h8 amount=99.99
```

---

## Complete Message Flow (Visual)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Developer)                                │
│                  curl -X POST http://localhost:8082/payments/test            │
│                      -H "X-Correlation-Id: demo-123"                         │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ HTTP POST
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PAYMENT-SERVICE (Port 8082)                          │
│                                                                              │
│  1. CorrelationIdFilter                                                      │
│     └─ MDC.set("correlationId", "demo-123")                                 │
│                                                                              │
│  2. PaymentTestController.testPaymentEvent()                                │
│     └─ Calls PaymentTestService.publishTestPaymentEvent("demo-123")         │
│                                                                              │
│  3. PaymentTestService.publishTestPaymentEvent()                            │
│     ├─ Creates PaymentCreatedEvent (paymentId, userId, amount, etc)         │
│     ├─ Calls kafkaTemplate.send(                                            │
│     │    topic="payment.events",                                            │
│     │    key="pay_a1b2c3d4e5f6g7h8",                                        │
│     │    value=[JSON event]                                                 │
│     │  )                                                                     │
│     └─ Waits 30s for Kafka acknowledgment                                   │
│                                                                              │
│  4. Returns HTTP 201 CREATED + event JSON                                   │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ HTTP 201
                                      │ ┌─────────────────┐
                                      │ │ JSON Response   │
                                      │ │ (event payload) │
                                      │ └─────────────────┘
                                      │
                                      │ ALSO sends to Kafka:
                                      │ Topic: payment.events
                                      │ Key: pay_a1b2c3d4e5f6g7h8
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KAFKA BROKER (Port 9092)                            │
│                                                                              │
│  Topic: payment.events (3 partitions, replication factor 1)                 │
│  ├─ Partition 0: [offset 0, 1, 2, ...]                                     │
│  ├─ Partition 1: [offset 0, 1, 2, ...]                                     │
│  └─ Partition 2: [offset 0, 1, 2, ...]                                     │
│                                                                              │
│  Message stored:                                                            │
│  {                                                                           │
│    key: "pay_a1b2c3d4e5f6g7h8",                                             │
│    value: {                                                                 │
│      paymentId: "pay_a1b2c3d4e5f6g7h8",                                     │
│      userId: "user_xyz123abc456",                                           │
│      amount: 99.99,                                                         │
│      currency: "USD",                                                       │
│      status: "INITIATED",                                                   │
│      timestamp: "2026-05-22T10:15:30.123Z",                                 │
│      correlationId: "demo-123"                                              │
│    }                                                                        │
│  }                                                                           │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ Kafka subscribes
                                      │ (Consumer group: ledger-group)
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LEDGER-SERVICE (Port 8084)                          │
│                                                                              │
│  1. PaymentEventConsumer (async Kafka listener)                             │
│     ├─ @KafkaListener(                                                      │
│     │    topics="payment.events",                                           │
│     │    groupId="ledger-group"                                             │
│     │  )                                                                     │
│     ├─ Receives: PaymentCreatedEvent                                        │
│     ├─ Extracts correlationId: "demo-123"                                   │
│     ├─ Sets MDC.set("correlationId", "demo-123")                            │
│     ├─ Logs: "event=payment_event_received"                                 │
│     └─ Calls LedgerService.processPaymentEvent(event)                       │
│                                                                              │
│  2. LedgerService.processPaymentEvent(event)                                │
│     ├─ Validates event.paymentId is not null                                │
│     ├─ Thread.sleep(25ms)  // Simulate DB latency                           │
│     ├─ Logs: "event=ledger_simulated_write"                                 │
│     └─ In real system: INSERT INTO ledger_entries (...)                     │
│                                                                              │
│  3. Auto-commits Kafka offset (message processed successfully)              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           RESULT (Timeline)                                 │
│                                                                              │
│  T+0ms     Client sends HTTP POST                                           │
│  T+5ms     CorrelationIdFilter sets MDC                                     │
│  T+10ms    PaymentTestController receives request                           │
│  T+15ms    PaymentCreatedEvent created & published to Kafka                 │
│  T+20ms    HTTP 201 response sent back to client                            │
│  T+25ms    Ledger service receives event from Kafka                         │
│  T+50ms    Ledger processing complete (25ms sleep)                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Files You MUST Understand First (In Order)

1. **[services/payment-service/src/main/java/dev/pulsepay/payment/controller/PaymentTestController.java](services/payment-service/src/main/java/dev/pulsepay/payment/controller/PaymentTestController.java)** - Entry point for HTTP
2. **[services/payment-service/src/main/java/dev/pulsepay/payment/service/PaymentTestService.java](services/payment-service/src/main/java/dev/pulsepay/payment/service/PaymentTestService.java)** - Kafka publishing logic
3. **[services/payment-service/src/main/java/dev/pulsepay/payment/event/PaymentCreatedEvent.java](services/payment-service/src/main/java/dev/pulsepay/payment/event/PaymentCreatedEvent.java)** - Event schema
4. **[services/ledger-service/src/main/java/dev/pulsepay/ledger/consumer/PaymentEventConsumer.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/consumer/PaymentEventConsumer.java)** - Kafka consumption
5. **[services/ledger-service/src/main/java/dev/pulsepay/ledger/service/LedgerService.java](services/ledger-service/src/main/java/dev/pulsepay/ledger/service/LedgerService.java)** - Event processing

## Files You Can IGNORE For Now

- **fraud-detection/** - Not used in Level 1 MVP
- **wallet-service/** - Not used in Level 1 MVP
- **audit-service/** - Not used in Level 1 MVP
- **common/** - Shared utilities (read later if needed)
- **Most of pulsepay/infra/** - Alternate Docker setup

---

## Running the System (Practical Steps)

### Option A: Docker Compose (Recommended)

```bash
# Terminal 1: Start full stack (Docker)
cd d:/Files/GithubDesktop/PulsePay
docker compose -f infra/docker/docker-compose.yml up --build

# Wait for output: "ledger-service | started"
# Then proceed to testing
```

### Option B: Local JVM + Docker Kafka

```bash
# Terminal 1: Start Kafka infrastructure only
cd d:/Files/GithubDesktop/PulsePay
docker compose -f infra/docker/docker-compose.yml up -d zookeeper kafka kafka-ui

# Terminal 2: Start ledger-service
cd d:/Files/GithubDesktop/PulsePay
mvn -pl services/ledger-service spring-boot:run

# Terminal 3: Start payment-service
cd d:/Files/GithubDesktop/PulsePay
mvn -pl services/payment-service spring-boot:run
```

---

## Testing the Flow (Step-by-Step)

### Step 1: Send HTTP Request

```bash
curl -i -X POST "http://localhost:8082/payments/test" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: debug-flow-001"
```

**Expected Response**:
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

### Step 2: Watch payment-service Logs

```bash
# If using Docker Compose:
docker compose -f infra/docker/docker-compose.yml logs -f payment-service

# If using local JVM (terminal 3), logs appear in terminal
```

**Logs you should see** (in order):
```
correlationId=debug-flow-001 - Request intercepted by CorrelationIdFilter
correlationId=debug-flow-001 event=payment_event_created paymentId=pay_a1b2c3d4e5f6g7h8
correlationId=debug-flow-001 event=payment_event_published_to_kafka topic=payment.events
```

### Step 3: Watch ledger-service Logs

```bash
# If using Docker Compose:
docker compose -f infra/docker/docker-compose.yml logs -f ledger-service

# If using local JVM (terminal 2), logs appear in terminal
```

**Logs you should see** (in order, ~25ms after payment-service logs):
```
correlationId=debug-flow-001 event=payment_event_received paymentId=pay_a1b2c3d4e5f6g7h8
correlationId=debug-flow-001 event=ledger_simulated_write amount=99.99 currency=USD status=INITIATED
```

### Step 4: Verify Message in Kafka UI

Open browser: **http://localhost:8090**

1. Click **Brokers** → Confirm 1 broker listed
2. Click **Topics** → Select **payment.events**
3. Click **Messages** tab
4. You should see:
   - Topic: `payment.events`
   - Partition: 0, 1, or 2 (depends on paymentId hash)
   - Offset: 0, 1, 2, etc (incremented per message)
   - Key: `pay_a1b2c3d4e5f6g7h8`
   - Value: JSON with all event fields

---

## Logs to Watch During Debugging

### Log Pattern Format
```
correlationId=[YOUR_ID] event=[EVENT_TYPE] [key=value] ...
```

### Key Events in Payment Flow

| Event | Service | Log Pattern | Meaning |
|-------|---------|-------------|---------|
| `payment_event_created` | payment-service | `event=payment_event_created paymentId=...` | Event object created in memory |
| `payment_event_published_to_kafka` | payment-service | `event=payment_event_published_to_kafka topic=payment.events` | Kafka send() called + acknowledged |
| `payment_event_received` | ledger-service | `event=payment_event_received paymentId=...` | Kafka consumer received message |
| `ledger_simulated_write` | ledger-service | `event=ledger_simulated_write amount=... currency=...` | Ledger processing done |

### How to Extract Logs by correlationId

```bash
# Filter Docker logs by correlationId
docker compose -f infra/docker/docker-compose.yml logs payment-service | grep "debug-flow-001"
docker compose -f infra/docker/docker-compose.yml logs ledger-service | grep "debug-flow-001"
```

---

## Debugging Checklist

### If HTTP Request Fails (Step 1)

```bash
# Check payment-service is running
curl http://localhost:8082/actuator/health

# Check Kafka connectivity from payment-service
docker logs payment-service | grep -i "kafka\|bootstrap"
```

### If payment-service Publishes But ledger-service Doesn't Receive

```bash
# Check Kafka topic exists
docker exec kafka kafka-topics.sh --list --bootstrap-server kafka:9092 | grep payment.events

# Check consumer group exists
docker exec kafka kafka-consumer-groups.sh --list --bootstrap-server kafka:9092 | grep ledger-group

# Check consumer group status
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 \
  --group ledger-group \
  --describe
```

### If Message Not Visible in Kafka UI

```bash
# Check Kafka UI is running
curl http://localhost:8090

# Check Kafka UI can connect to broker
docker logs kafka-ui | grep -i "error\|connection"
```

---

## Summary

**What happens when you POST /payments/test:**

1. **HTTP Request** → CorrelationIdFilter adds correlationId to MDC
2. **Controller** extracts correlationId, calls service
3. **Service** creates PaymentCreatedEvent, publishes to Kafka
4. **Kafka** stores message in topic `payment.events`
5. **Consumer** in ledger-service receives message
6. **Ledger Service** simulates processing (logs it)

**Timeline**: ~50ms total (payment-service ~20ms, Kafka instant, ledger-service ~25ms)

**Correlation**: All logs include `correlationId=debug-flow-001` so you can trace a single request end-to-end.

**Key files to read first**: Controller → Service → Event → Consumer → LedgerService (in that order)

---

## Interview Explanation

**What payment-service does:**
"Payment-service is a REST API that receives payment requests. When you POST /payments/test, it creates a PaymentCreatedEvent with payment details and publishes it to Kafka. It returns immediately with HTTP 201, so the client gets a response quickly. The actual processing happens asynchronously in the ledger-service."

**What Kafka does:**
"Kafka is a message broker. It decouples payment-service from ledger-service. Payment-service publishes to topic `payment.events`, and Kafka stores the message. Ledger-service subscribes to the same topic and receives messages as a consumer. If ledger-service is down, Kafka keeps the messages until ledger-service comes back online."

**What ledger-service does:**
"Ledger-service listens for PaymentCreatedEvent messages from Kafka. When it receives one, it simulates ledger processing (like writing to a database). In the real system, it would INSERT into a ledger table, but for now it just logs. The key point: it's a consumer, not an API. It processes events asynchronously."

