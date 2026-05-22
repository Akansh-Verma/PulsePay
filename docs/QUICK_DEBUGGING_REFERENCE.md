# PulsePay Quick Debugging Reference

## One-Liner Commands You'll Use Most

### Starting the System

```bash
# Full Docker Compose (Recommended)
docker compose -f infra/docker/docker-compose.yml up --build

# Local JVM + Docker Kafka
docker compose -f infra/docker/docker-compose.yml up -d zookeeper kafka kafka-ui
mvn -pl services/ledger-service spring-boot:run  # Terminal 2
mvn -pl services/payment-service spring-boot:run # Terminal 3
```

### Testing Single Request

```bash
# Simple test
curl -X POST "http://localhost:8082/payments/test"

# With correlation ID
curl -X POST "http://localhost:8082/payments/test" \
  -H "X-Correlation-Id: my-trace-001"

# Verbose (see all HTTP details)
curl -v -X POST "http://localhost:8082/payments/test" \
  -H "X-Correlation-Id: my-trace-001"
```

### Watching Logs (Docker Compose)

```bash
# Watch payment-service in real-time
docker compose -f infra/docker/docker-compose.yml logs -f payment-service

# Watch ledger-service in real-time
docker compose -f infra/docker/docker-compose.yml logs -f ledger-service

# Watch both services simultaneously
docker compose -f infra/docker/docker-compose.yml logs -f payment-service ledger-service

# Watch Kafka
docker compose -f infra/docker/docker-compose.yml logs -f kafka

# View last 50 lines
docker compose -f infra/docker/docker-compose.yml logs --tail=50 payment-service
```

### Filter Logs by Correlation ID

```bash
# Find all logs for a specific request
CORR_ID="my-trace-001"

# Payment-service logs only
docker compose -f infra/docker/docker-compose.yml logs payment-service | grep "$CORR_ID"

# Ledger-service logs only
docker compose -f infra/docker/docker-compose.yml logs ledger-service | grep "$CORR_ID"

# Both services together
docker compose -f infra/docker/docker-compose.yml logs payment-service ledger-service | grep "$CORR_ID"

# Pretty print (with timestamps)
docker compose -f infra/docker/docker-compose.yml logs payment-service | grep "$CORR_ID" | nl
```

### Kafka UI (Web Browser)

```
Open: http://localhost:8090

Navigation:
1. Brokers → Check Kafka is healthy
2. Topics → Select "payment.events"
3. Messages → View recent messages
   - Partition: 0, 1, 2
   - Offset: Incrementing number
   - Key: paymentId
   - Value: Full JSON event
4. Consumer Groups → Select "ledger-group"
   - Check LAG (should be near 0)
   - If LAG is high, ledger-service is behind
```

### Kafka CLI Inside Container

```bash
# List topics
docker exec kafka kafka-topics.sh --list --bootstrap-server kafka:9092

# Describe payment.events topic
docker exec kafka kafka-topics.sh \
  --describe \
  --topic payment.events \
  --bootstrap-server kafka:9092

# List consumer groups
docker exec kafka kafka-consumer-groups.sh \
  --list \
  --bootstrap-server kafka:9092

# Describe ledger-group consumer status
docker exec kafka kafka-consumer-groups.sh \
  --describe \
  --group ledger-group \
  --bootstrap-server kafka:9092

# Consume messages (from offset 0)
docker exec kafka kafka-console-consumer.sh \
  --topic payment.events \
  --from-beginning \
  --bootstrap-server kafka:9092 \
  --max-messages 5

# Consume only new messages
docker exec kafka kafka-console-consumer.sh \
  --topic payment.events \
  --bootstrap-server kafka:9092
```

### Health Checks

```bash
# Payment-service health
curl http://localhost:8082/actuator/health

# Ledger-service health
curl http://localhost:8084/actuator/health

# Full Kafka connection check
curl http://localhost:8082/actuator/health/diskSpace
```

### Stopping & Cleaning

```bash
# Stop Docker Compose (keeps volumes)
docker compose -f infra/docker/docker-compose.yml down

# Stop and remove everything (volumes too)
docker compose -f infra/docker/docker-compose.yml down -v

# Kill all running containers
docker kill $(docker ps -q)

# Remove all stopped containers
docker container prune -f
```

---

## Debugging Workflow (Step-by-Step)

### Scenario: Request Stuck / Not Processing

```bash
# 1. Send test request with unique correlation ID
CORR_ID="debug-$(date +%s)"
curl -X POST "http://localhost:8082/payments/test" \
  -H "X-Correlation-Id: $CORR_ID"
echo "Used correlation ID: $CORR_ID"

# 2. Check payment-service logs
docker compose -f infra/docker/docker-compose.yml logs payment-service | grep "$CORR_ID"

# 3. Check if message reached Kafka
docker exec kafka kafka-console-consumer.sh \
  --topic payment.events \
  --from-beginning \
  --bootstrap-server kafka:9092 \
  --max-messages 1

# 4. Check ledger-service logs
docker compose -f infra/docker/docker-compose.yml logs ledger-service | grep "$CORR_ID"

# 5. Check consumer group lag
docker exec kafka kafka-consumer-groups.sh \
  --describe \
  --group ledger-group \
  --bootstrap-server kafka:9092

# 6. If LAG is high, check if ledger-service is running
docker compose -f infra/docker/docker-compose.yml ps ledger-service

# 7. View ledger-service full logs
docker compose -f infra/docker/docker-compose.yml logs ledger-service --tail=100
```

### Scenario: Want to See Exact JSON in Kafka

```bash
# Method 1: Kafka UI (easiest)
# http://localhost:8090 → Topics → payment.events → Messages

# Method 2: Command line (get last message)
docker exec kafka kafka-console-consumer.sh \
  --topic payment.events \
  --bootstrap-server kafka:9092 \
  --from-beginning \
  --max-messages 1 \
  --property print.key=true | tail -n 1 | jq '.'

# Method 3: Get N recent messages
docker exec kafka kafka-console-consumer.sh \
  --topic payment.events \
  --bootstrap-server kafka:9092 \
  --max-messages 3
```

### Scenario: Verify End-to-End Flow Works

```bash
# 1. Start system
docker compose -f infra/docker/docker-compose.yml up --build

# Wait for "ledger-service | started"

# 2. Send request in separate terminal
CORR_ID="e2e-test-$(date +%s)"
echo "Sending request with ID: $CORR_ID"
curl -X POST "http://localhost:8082/payments/test" \
  -H "X-Correlation-Id: $CORR_ID" | jq '.'

# 3. Immediately check payment logs
echo "=== PAYMENT SERVICE LOGS ==="
docker compose -f infra/docker/docker-compose.yml logs payment-service | grep "$CORR_ID"

# 4. Check Kafka message
echo "=== KAFKA TOPIC ==="
docker exec kafka kafka-console-consumer.sh \
  --topic payment.events \
  --bootstrap-server kafka:9092 \
  --from-beginning \
  --max-messages 1

# 5. Wait 1 second, check ledger logs
sleep 1
echo "=== LEDGER SERVICE LOGS ==="
docker compose -f infra/docker/docker-compose.yml logs ledger-service | grep "$CORR_ID"

# Success if all 3 show the same correlationId!
```

---

## Log Patterns to Search For

```bash
# Find all events published
docker compose -f infra/docker/docker-compose.yml logs payment-service | grep "payment_event_published"

# Find all events received by ledger
docker compose -f infra/docker/docker-compose.yml logs ledger-service | grep "payment_event_received"

# Find all ledger writes
docker compose -f infra/docker/docker-compose.yml logs ledger-service | grep "ledger_simulated_write"

# Find errors
docker compose -f infra/docker/docker-compose.yml logs | grep -i "ERROR\|Exception\|failed"

# Find Kafka connection issues
docker compose -f infra/docker/docker-compose.yml logs | grep -i "kafka\|bootstrap"
```

---

## Docker Compose Cheat Sheet

```bash
# View all running services
docker compose -f infra/docker/docker-compose.yml ps

# View specific service logs
docker compose -f infra/docker/docker-compose.yml logs payment-service

# Enter container shell
docker exec -it payment-service /bin/bash

# Restart a service
docker compose -f infra/docker/docker-compose.yml restart payment-service

# Scale a service (not recommended for stateful services)
docker compose -f infra/docker/docker-compose.yml up --scale ledger-service=2

# View resource usage
docker stats

# Check specific service resources
docker stats payment-service
```

---

## Common Issues & Quick Fixes

### Issue: "Connection refused" to localhost:8082

**Cause**: payment-service not running or not ready

**Fix**:
```bash
# Check if container is running
docker compose -f infra/docker/docker-compose.yml ps payment-service

# If not running, rebuild
docker compose -f infra/docker/docker-compose.yml up --build payment-service

# Check startup logs
docker compose -f infra/docker/docker-compose.yml logs payment-service --tail=50
```

### Issue: Ledger-service not consuming messages (LAG growing)

**Cause**: Consumer not running or group doesn't exist

**Fix**:
```bash
# Check if ledger-service is running
docker compose -f infra/docker/docker-compose.yml ps ledger-service

# Check consumer group
docker exec kafka kafka-consumer-groups.sh \
  --describe \
  --group ledger-group \
  --bootstrap-server kafka:9092

# If empty, restart ledger-service to recreate group
docker compose -f infra/docker/docker-compose.yml restart ledger-service
```

### Issue: "Topic 'payment.events' doesn't exist"

**Cause**: Kafka topics not created

**Fix**:
```bash
# Check if topic exists
docker exec kafka kafka-topics.sh --list --bootstrap-server kafka:9092

# If missing, create it
docker exec kafka kafka-topics.sh \
  --create \
  --topic payment.events \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server kafka:9092

# Or restart docker compose to recreate
docker compose -f infra/docker/docker-compose.yml down -v
docker compose -f infra/docker/docker-compose.yml up
```

### Issue: Can't reach Kafka UI (http://localhost:8090)

**Cause**: kafka-ui container not running or not healthy

**Fix**:
```bash
# Check status
docker compose -f infra/docker/docker-compose.yml ps kafka-ui

# View logs
docker compose -f infra/docker/docker-compose.yml logs kafka-ui

# Restart
docker compose -f infra/docker/docker-compose.yml restart kafka-ui

# If still failing, check if port 8090 is in use
netstat -an | grep 8090  # (on Windows: netstat -ano | findstr 8090)
```

---

## Performance Baseline

These are typical timing measurements:

| Step | Time | How to Measure |
|------|------|----------------|
| HTTP Request → Response (201) | ~15-20ms | `curl -w "@curl-format.txt"` |
| Kafka Publish + Ack | ~5-10ms | Embedded in above |
| Kafka Message Available | ~1-2ms | Check offset with Kafka CLI |
| Ledger Consumer Processes | ~25-50ms | See ledger logs delay |
| Total Request → Ledger Completed | ~50-70ms | From request to final log |

If any of these are significantly higher, check:
- Docker resource limits
- Network latency (docker inspect)
- Kafka broker health
- Service logs for errors

---

## File Locations (Reference)

```
d:\Files\GithubDesktop\PulsePay\
├─ services/
│  ├─ payment-service/
│  │  ├─ src/main/java/dev/pulsepay/payment/
│  │  │  ├─ PaymentServiceApplication.java
│  │  │  ├─ controller/PaymentTestController.java
│  │  │  ├─ service/PaymentTestService.java
│  │  │  ├─ event/PaymentCreatedEvent.java
│  │  │  ├─ config/KafkaConfig.java
│  │  │  └─ filter/CorrelationIdFilter.java
│  │  └─ src/main/resources/
│  │     └─ application.yml
│  │
│  └─ ledger-service/
│     ├─ src/main/java/dev/pulsepay/ledger/
│     │  ├─ LedgerServiceApplication.java
│     │  ├─ consumer/PaymentEventConsumer.java
│     │  ├─ service/LedgerService.java
│     │  ├─ event/PaymentCreatedEvent.java
│     │  └─ config/KafkaConfig.java
│     └─ src/main/resources/
│        └─ application.yml
│
├─ infra/docker/
│  └─ docker-compose.yml
│
└─ docs/
   ├─ LEVEL1.md                      (Setup instructions)
   ├─ RUNTIME_FLOW_DEBUGGING.md     (This comprehensive guide)
   └─ SEQUENCE_DIAGRAM.md           (Visual flows)
```

