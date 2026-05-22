# Ledger Service

A minimal Spring Boot 3 microservice that consumes payment events from Kafka and processes them into ledger entries.

## Overview

The **Ledger Service** is a specialized Kafka consumer built with Spring Boot 3 and Java 21. It:

- Consumes `PaymentCreatedEvent` from the `payment.events` Kafka topic
- Processes payment events and simulates ledger entry creation
- Includes correlation IDs in all logs for distributed tracing
- Handles errors gracefully without crashing
- Uses manual acknowledgment to prevent message loss

## Architecture

```
payment-service (publishes PaymentCreatedEvent)
        ↓
   payment.events (Kafka topic)
        ↓
ledger-service (consumes & processes)
        ↓
   Simulated Ledger (no database)
```

## Key Features

### 1. **Kafka Consumer**
- Topic: `payment.events`
- Consumer Group: `ledger-group`
- Deserializes Avro-serialized `PaymentCreatedEvent` messages
- Uses manual acknowledgment for reliability

### 2. **Event Processing**
- Extracts payment details (paymentId, userId, merchantId, amount, currency, rail)
- Simulates ledger entry creation with `Thread.sleep(50)` to mimic database latency
- Logs all processing steps with correlation IDs

### 3. **Error Handling**
- Graceful error handling in the consumer
- Errors are logged but do not crash the application
- Manual acknowledgment happens even on error (prevents infinite retries)
- Implements `ErrorHandlingDeserializer` for robustness

### 4. **Correlation IDs**
- Extracts `correlationId` from event metadata
- Includes in all log messages for request tracing across services

## Project Structure

```
services/ledger-service/
├── src/main/java/dev/pulsepay/ledger/
│   ├── LedgerServiceApplication.java        # Spring Boot entry point
│   ├── kafka/
│   │   ├── KafkaConfig.java                 # Kafka consumer configuration
│   │   └── LedgerEventConsumer.java         # Kafka message listener
│   └── service/
│       └── LedgerService.java               # Business logic
├── src/main/resources/
│   └── application.yml                      # Configuration
└── pom.xml                                  # Maven configuration
```

## Configuration

### `application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: ledger-group
      auto-offset-reset: earliest
```

**Key Properties:**
- `bootstrap-servers`: Kafka broker address
- `group-id`: Consumer group for offset management
- `auto-offset-reset`: Start from the earliest message if no offset exists

## Running the Service

### 1. Build the Project

```bash
cd PulsePay
mvn clean install
```

### 2. Start Kafka (if not already running)

```bash
# Using Docker Compose or local Kafka
docker-compose up -d kafka zookeeper
```

### 3. Run the Ledger Service

```bash
mvn spring-boot:run -pl services/ledger-service
```

Or:

```bash
java -jar services/ledger-service/target/ledger-service-1.0.0-SNAPSHOT.jar
```

### 4. Verify It's Running

```bash
curl http://localhost:8084/api/health
```

## Example Log Output

When the service processes a payment event:

```
2024-04-22 10:15:34 - [correlationId=trace-123] Received PaymentCreatedEvent: partition=0, offset=42, paymentId=pay-001, userId=user-123
2024-04-22 10:15:34 - [correlationId=trace-123] Creating ledger entry for payment: eventId=evt-456, paymentId=pay-001, userId=user-123, merchantId=merchant-789, amount=99.99 INR, rail=UPI
2024-04-22 10:15:34 - [correlationId=trace-123] Ledger entry created successfully for paymentId=pay-001
2024-04-22 10:15:34 - [correlationId=trace-123] Event processed successfully
```

## Event Model

The service consumes `PaymentCreatedEvent` from Avro schema:

```java
PaymentCreatedEvent {
  metadata: PulsePayEventMetadata  // eventId, correlationId, timestamp, etc.
  paymentId: String
  userId: String
  merchantId: String
  amount: Double
  currency: String                 // e.g., "INR"
  rail: PaymentRail                // UPI, CARD, WALLET, etc.
  status: PaymentStatus
}
```

## Dependency Chain

```
ledger-service
├── kafka-lib                       (Avro event models)
├── spring-boot-starter-web         (REST, logging)
├── spring-kafka                    (Kafka consumer)
└── lombok                          (Reduce boilerplate)
```

## Error Handling Flow

1. **Deserialization Error** → Log warning, skip message, continue
2. **Business Logic Error** → Log error, acknowledge message, continue
3. **Unhandled Exception** → Catch all, log, acknowledge, resume consumer

No error will crash the service.

## Testing

Currently minimal setup. To test:

1. Publish a `PaymentCreatedEvent` to `payment.events` using the payment-service
2. Observe logs in the ledger-service
3. Verify correlation IDs appear in output

## Future Enhancements

- [ ] Add database persistence (e.g., PostgreSQL with JPA)
- [ ] Add REST endpoints for ledger queries
- [ ] Add distributed tracing (Spring Cloud Sleuth)
- [ ] Add metrics (Micrometer)
- [ ] Add health checks for Kafka connectivity
- [ ] Add unit and integration tests

## Dependencies

- **Java**: 21
- **Spring Boot**: 3.2.5
- **Spring Kafka**: 3.0+
- **Avro**: 1.11+
- **Lombok**: 1.18+

## Development Notes

- The `LedgerService` currently simulates ledger updates with a 50ms sleep
- No database is configured yet; all processing is in-memory
- Manual acknowledgment prevents message loss in case of errors
- Correlation IDs are extracted from event metadata for distributed tracing

## Support

For issues or questions:
1. Check application logs for correlation IDs
2. Verify Kafka connectivity: `kafka-console-consumer --topic payment.events --from-beginning`
3. Ensure payment-service is publishing events correctly

