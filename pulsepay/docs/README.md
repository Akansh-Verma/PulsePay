# PulsePay Monorepo

PulsePay is a production-grade, event-driven payment platform built on Java 21 and Spring Boot 3.x. This monorepo contains all four core microservices, a shared common library, and supporting infrastructure configuration.

## Directory Structure

```
pulsepay/
├── pom.xml                          # Maven parent POM (multi-module root)
├── common/
│   └── common-lib/                  # Shared library: DTOs, events, utilities, Kafka constants
├── services/
│   ├── payment-service/             # Handles payment initiation and orchestration (port 8082)
│   ├── ledger-service/              # Records financial transactions (port 8083)
│   ├── fraud-service/               # Evaluates transactions for fraud risk (port 8085)
│   └── notification-service/        # Sends payment status notifications (port 8086)
├── infra/
│   ├── docker/
│   │   ├── docker-compose.yml       # Local dev stack (Kafka, Zookeeper, PostgreSQL, Redis, services)
│   │   ├── payment-service/Dockerfile
│   │   ├── ledger-service/Dockerfile
│   │   ├── fraud-service/Dockerfile
│   │   └── notification-service/Dockerfile
│   └── k8s/
│       └── namespace.yaml           # Kubernetes namespace: pulsepay
└── docs/
    └── README.md                    # This file
```

## Services

| Service | Directory | Port | Description |
|---|---|---|---|
| payment-service | [services/payment-service](../services/payment-service) | 8082 | Payment initiation and orchestration |
| ledger-service | [services/ledger-service](../services/ledger-service) | 8083 | Financial transaction recording |
| fraud-service | [services/fraud-service](../services/fraud-service) | 8085 | Fraud risk evaluation |
| notification-service | [services/notification-service](../services/notification-service) | 8086 | Payment status notifications |

## Shared Library

[common/common-lib](../common/common-lib) provides reusable components consumed by all services:

- `com.pulsepay.common.dto.BaseDto` — base DTO with `correlationId` field
- `com.pulsepay.common.event.BaseEvent` — abstract base event with `eventId`, `eventType`, `timestamp`, `correlationId`
- `com.pulsepay.common.util.CorrelationIdUtils` — UUID-format correlation ID generation and validation
- `com.pulsepay.common.config.KafkaHeaderConstants` — declares `CORRELATION_ID_HEADER = "X-Correlation-Id"`

## Building

Build all modules from the monorepo root:

```bash
mvn install -DskipTests
```

Run all tests:

```bash
mvn test
```

Build a single service:

```bash
mvn install -DskipTests -pl services/payment-service -am
```

## Correlation ID Propagation

Every request is assigned a `X-Correlation-Id` UUID header that flows through the entire system:

- **HTTP**: `CorrelationIdFilter` (extends `OncePerRequestFilter`) in each service reads the header on inbound requests, stores it in the MDC under key `correlationId`, and clears it after the request completes. If no header is present, a new UUID is generated.
- **Kafka**: `CorrelationIdProducerInterceptor` injects the MDC `correlationId` as a Kafka message header on every outbound message. `CorrelationIdConsumerInterceptor` extracts it from inbound message headers and restores it to the MDC before processing.
- **Logging**: All structured JSON log entries include the `correlationId` field sourced from the MDC, enabling end-to-end trace correlation in the ELK stack.

## Infrastructure

- **Local dev**: `infra/docker/docker-compose.yml` brings up Kafka, Zookeeper, PostgreSQL, Redis, and all four services.
- **Kubernetes**: `infra/k8s/namespace.yaml` defines the `pulsepay` namespace. Deploy service manifests into this namespace.
