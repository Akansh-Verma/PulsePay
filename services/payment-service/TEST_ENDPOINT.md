# Payment Service - Test Endpoint

## Overview
The Payment Service test endpoint provides a simple way to generate and publish dummy `PaymentCreatedEvent` messages to Kafka for testing and development purposes.

## Architecture

### Components

- **PaymentTestController**: REST endpoint handler
- **PaymentTestService**: Event generation and publication logic
- **PaymentCreatedEvent**: Event model (POJO with JSON serialization)
- **KafkaConfig**: Spring Kafka configuration with KafkaTemplate
- **CorrelationIdFilter**: Servlet filter for MDC correlation ID tracking

## API Endpoint

### POST /payments/test

**Description**: Generates a dummy PaymentCreatedEvent and publishes it to Kafka topic `payment.events`.

**Request Headers** (optional):
```
X-Correlation-Id: <correlation-id>  # Generated if not provided
X-Trace-Id: <trace-id>              # Populated in logging
```

**Response**: `201 Created`
```json
{
  "paymentId": "pay_a1b2c3d4e5f6g7h8",
  "userId": "user_x1y2z3a4b5c6",
  "amount": 99.99,
  "currency": "USD",
  "status": "INITIATED",
  "timestamp": "2026-04-22T15:30:45.123456Z",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Example Usage

### cURL
```bash
curl -X POST http://localhost:8082/payments/test \
  -H "X-Correlation-Id: test-correlation-123" \
  -H "Content-Type: application/json"
```

### Without Correlation ID (will be generated)
```bash
curl -X POST http://localhost:8082/payments/test
```

## Kafka Configuration

**Topic**: `payment.events`
**Bootstrap Server**: `localhost:9092` (configurable in `application.yml`)
**Message Key**: `paymentId` (ensures ordering per payment)
**Value Serialization**: JSON

### Configuration (application.yml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
```

## Event Fields

| Field | Type | Description |
|-------|------|-------------|
| paymentId | String | Generated unique payment identifier |
| userId | String | Generated test user identifier |
| amount | double | Fixed test amount (99.99) |
| currency | String | Currency code (USD) |
| status | String | Payment status (INITIATED) |
| timestamp | Instant | Event creation timestamp (UTC) |
| correlationId | String | Request correlation ID for tracing |

## Logging

All events are logged with correlation ID tracking enabled via MDC:

```
15:30:45 [traceId] [correlationId] INFO PaymentTestService - Test payment event published - paymentId: pay_a1b2c3d4e5f6g7h8, userId: user_x1y2z3a4b5c6, amount: 99.99 USD, correlationId: 550e8400...
```

## Running the Service

```bash
mvn spring-boot:run
```

Or build and run:
```bash
mvn clean package
java -jar target/payment-service-1.0.0-SNAPSHOT.jar
```

## Port
Default: `8082`

## Requirements

- Java 21+
- Spring Boot 3.2.5
- Kafka broker running on `localhost:9092`
- Maven 3.8+

## Notes

- Test events use dummy but realistic data
- Each test call generates a unique paymentId and userId
- Correlation ID enables distributed tracing across microservices
- No database or persistence layer (test-only endpoint)
- Events are published immediately without validation
