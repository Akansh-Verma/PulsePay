# PulsePay Level 1 MVP (local)

End-to-end flow: **POST /payments/test** on **payment-service** publishes a JSON **PaymentCreatedEvent** to **payment.events**; **ledger-service** consumes it (consumer group **ledger-group**) and logs simulated ledger processing.

Maven modules live under the repository root **`services/`** (see root `pom.xml`). Docker assets live under **`infra/docker/`** (canonical). Multi-stage images use **`infra/docker/reactor-pom.xml`**: a slim parent that lists only **payment-service** and **ledger-service**, so Docker builds do not load unrelated modules (for example **fraud-detection**, which uses a different parent coordinates in this repo).

A compose file also exists at **`pulsepay/infra/docker/docker-compose.yml`** for the same stack described in the product spec; it builds from the **repository root** context and reuses **`infra/docker/payment-service/Dockerfile`** and **`infra/docker/ledger-service/Dockerfile`**.

## Prerequisites

- **JDK 21** and **Maven 3.9+** (for local JVM runs), or **Docker Desktop** (for Compose).
- Ports **2181**, **9092**, **8082**, **8084**, **8090** available on the host when using Compose.

## Option A — Docker Compose (recommended)

From the **repository root** (`PulsePay/`):

```bash
docker compose -f infra/docker/docker-compose.yml up --build
```

Wait until **payment-service** and **ledger-service** start after Kafka is healthy. Then:

### Test with curl

```bash
curl -i -X POST "http://localhost:8082/payments/test" -H "X-Correlation-Id: demo-correlation-123"
```

Optional header **X-Correlation-Id** is forwarded into logs via MDC; if omitted, **payment-service** generates one in **CorrelationIdFilter**.

### Expected behavior

1. HTTP **201** with a JSON body containing **paymentId**, **userId**, **amount**, **currency**, **status**, **timestamp**, **correlationId**.
2. **ledger-service** logs lines such as **`event=payment_event_received`** and **`event=ledger_simulated_write`** including **correlationId**.

View logs:

```bash
docker compose -f infra/docker/docker-compose.yml logs -f ledger-service
```

### Kafka UI

Open **http://localhost:8090** (mapped from container port 8080). Cluster bootstrap inside Docker is **kafka:29092**; from the host, brokers are on **localhost:9092**.

---

## Option B — Local JVM (Kafka in Docker only)

Start only infrastructure:

```bash
docker compose -f infra/docker/docker-compose.yml up -d zookeeper kafka kafka-ui
```

Then from the repository root:

```bash
mvn -pl services/payment-service,services/ledger-service spring-boot:run -DskipTests
```

That runs both apps in one terminal only if you use two terminals or run one module at a time, for example:

```bash
# Terminal 1
mvn -pl services/ledger-service spring-boot:run

# Terminal 2
mvn -pl services/payment-service spring-boot:run
```

Ensure **`SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092`** (default in `application.yml`).

Same **curl** as above against **http://localhost:8082**.

---

## Project layout (Level 1)

| Area | Location |
|------|----------|
| payment-service | `services/payment-service/` (`controller`, `service`, `event`, `config`, `filter`) |
| ledger-service | `services/ledger-service/` (`consumer`, `service`, `event`, `config`) |
| Compose + Dockerfiles | `infra/docker/` |
| This doc | `docs/LEVEL1.md` |

Topic: **payment.events**. Consumer group: **ledger-group**.
