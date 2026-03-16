<div align="center">

```
██████╗ ██╗   ██╗██╗     ███████╗███████╗██████╗  █████╗ ██╗   ██╗
██╔══██╗██║   ██║██║     ██╔════╝██╔════╝██╔══██╗██╔══██╗╚██╗ ██╔╝
██████╔╝██║   ██║██║     ███████╗█████╗  ██████╔╝███████║ ╚████╔╝
██╔═══╝ ██║   ██║██║     ╚════██║██╔══╝  ██╔═══╝ ██╔══██║  ╚██╔╝
██║     ╚██████╔╝███████╗███████║███████╗██║     ██║  ██║   ██║
╚═╝      ╚═════╝ ╚══════╝╚══════╝╚══════╝╚═╝     ╚═╝  ╚═╝   ╚═╝
```

**A simulated multi-rail digital payment infrastructure**
*Scalable transaction processing · Event-driven architecture · Fraud detection*

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-3.x-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-EKS-326CE5?style=flat-square&logo=kubernetes&logoColor=white)](https://kubernetes.io/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)
[![Status](https://img.shields.io/badge/Status-In_Development-yellow?style=flat-square)]()

</div>

---

## What is PulsePay?

PulsePay is a **production-grade, event-driven payment platform** built to demonstrate how modern fintech infrastructure is designed and operated at scale. It simulates a real-world multi-rail payment system supporting UPI, card, wallet, and BNPL flows — backed by a Spring Boot 3 microservices architecture with Apache Kafka at its core.

> This is not a toy. Every design decision — from polyglot persistence to Avro schema contracts, Kafka topic partitioning, and distributed tracing — mirrors what you'd find inside a regulated payment processor.

**What it demonstrates:**
- End-to-end payment lifecycle with idempotency, retries, and failure handling
- Event-driven inter-service communication via Kafka with Avro schemas
- Rule-based fraud detection with velocity checks using Redis
- Distributed tracing across 8 microservices with Zipkin
- Infrastructure-as-code deployment to AWS (EKS, RDS Aurora, MSK)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                           CLIENTS                               │
│   Next.js Web  ·  React Native Mobile  ·  Merchant SDK          │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS
┌────────────────────────────▼────────────────────────────────────┐
│                  SPRING CLOUD GATEWAY                           │
│          JWT Validation  ·  Rate Limiting  ·  Routing           │
└──────┬──────────┬──────────┬──────────┬──────────┬─────────────┘
       │          │          │          │          │
┌──────▼─┐  ┌────▼───┐  ┌───▼────┐  ┌──▼─────┐  ┌▼────────┐
│  auth  │  │payment │  │ wallet │  │  user  │  │ report  │
│service │  │service │  │service │  │service │  │service  │
└────────┘  └────┬───┘  └───┬────┘  └────────┘  └─────────┘
                 │           │
    ┌────────────▼───────────▼──────────────────────────────┐
    │                   APACHE KAFKA                        │
    │  payment.events · wallet.events · fraud.alerts        │
    │  notification.requests · audit.events                 │
    └──────┬─────────────┬──────────────┬───────────────────┘
           │             │              │
    ┌──────▼───┐  ┌──────▼──────┐  ┌───▼────────────┐
    │  fraud   │  │notification │  │  audit service │
    │detection │  │  service    │  │  (append-only) │
    └──────────┘  └─────────────┘  └────────────────┘
```

### Core Event Flow

| Step | Service | Action |
|------|---------|--------|
| 1 | **Client** | Submits payment request via Gateway |
| 2 | **Gateway** | Validates JWT, checks rate limit, routes to Payment Service |
| 3 | **Payment Service** | Validates, checks idempotency key in Redis, calls rail, writes to PostgreSQL |
| 4 | **Kafka** | `PaymentCreated` Avro event published to `payment.events` |
| 5 | **Wallet Service** | Consumes event, performs atomic debit/credit, emits `wallet.events` |
| 6 | **Fraud Detection** | Consumes in parallel, runs velocity rules via Redis, publishes to `fraud.alerts` if flagged |
| 7 | **Notification Service** | Consumes `notification.requests`, dispatches push / SMS / email |
| 8 | **Merchant / Audit** | Order state updated; immutable audit record written to MongoDB |

---

## Technology Stack

### Backend — Core Services

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language — virtual threads (Project Loom) for high-concurrency I/O |
| Spring Boot | 3.x | Service framework across all 8 microservices |
| Spring Cloud Gateway | 4.x | Single entry point — JWT filter, rate limiter, circuit breaker |
| Spring Security | 6.x | OAuth 2.0 / JWT in `auth-service` |
| Spring Cloud Config | 4.x | Centralised Git-backed configuration server |
| Eureka | 2.x | Service registry and dynamic routing |
| Resilience4j | 2.x | Circuit breaker, retry, bulkhead patterns |

### Messaging — Event Streaming

| Technology | Version | Purpose |
|------------|---------|---------|
| Apache Kafka | 3.x | Distributed event streaming — backbone of all async flows |
| Avro | 1.11 | Binary serialisation for all Kafka messages |
| Schema Registry | 7.x | Confluent Schema Registry — enforces Avro schema evolution contracts |

### Data Storage — Polyglot Persistence

| Technology | Version | Purpose |
|------------|---------|---------|
| PostgreSQL | 15 | Transactional data — payments, wallets, ledger, users |
| Redis | 7 | Sessions, OTP cache, idempotency keys, fraud velocity counters |
| MongoDB | 7 | Audit logs, KYC artefacts, webhook payload storage |
| Elasticsearch | 8 | Transaction search, merchant analytics, dispute resolution |

### Observability

| Technology | Purpose |
|------------|---------|
| Micrometer | Instrumentation facade embedded in every Spring Boot service |
| Prometheus | Time-series metrics scraping and alerting |
| Grafana | Dashboards — throughput, p99 latency, Kafka lag, fraud signal rates |
| Zipkin | Distributed tracing — trace IDs propagated through Kafka message headers |
| ELK Stack | Structured log aggregation — Logstash → Elasticsearch → Kibana |

### Infrastructure

| Technology | Purpose |
|------------|---------|
| Docker | Multi-stage image builds; Docker Compose for local full-stack dev |
| Kubernetes | Production orchestration on AWS EKS — HPA, rolling deploys |
| Helm | Kubernetes manifest templating across dev / staging / prod |
| Terraform | AWS infra-as-code: RDS Aurora, MSK, ElastiCache, EKS, IAM |

---

## Repository Structure

```
pulsepay/
├── apps/
│   ├── web/                    # Next.js 14 consumer web app
│   ├── mobile/                 # React Native + Expo
│   ├── admin/                  # Next.js ops dashboard
│   └── api-gateway/            # Spring Cloud Gateway
│
├── services/
│   ├── auth-service/           # Spring Security · OAuth2 · JWT
│   ├── payment-service/        # Core payment processing · UPI · card rails
│   ├── wallet-service/         # Ledger · balances · transaction history
│   ├── user-service/           # KYC · profiles · onboarding
│   ├── fraud-detection/        # Rules engine · velocity checks · Redis
│   ├── notification-service/   # Push · SMS · email — Kafka consumer
│   ├── report-service/         # Settlement · reconciliation · analytics
│   └── audit-service/          # Append-only event log · compliance
│
├── libs/                       # Maven multi-module shared libraries
│   ├── common-lib/             # DTOs · Avro event schemas · utilities
│   ├── security-lib/           # JWT helpers · token validation
│   ├── kafka-lib/              # Producer/consumer factories · DLQ config
│   └── observability-lib/      # Micrometer · Sleuth auto-configuration
│
├── database/
│   ├── migrations/             # Flyway migrations (per service)
│   └── seeds/                  # Dev and staging fixture data
│
├── infra/
│   ├── docker/                 # Dockerfiles and docker-compose.yml
│   ├── k8s/                    # Helm charts · Kubernetes manifests
│   ├── terraform/              # AWS infrastructure-as-code
│   └── ci-cd/                  # GitHub Actions pipelines · ArgoCD
│
├── docs/
│   ├── api/                    # OpenAPI 3 specs · Swagger UI
│   ├── architecture/           # ADRs · system diagrams
│   └── runbooks/               # Ops procedures · incident response
│
├── pom.xml                     # Maven parent POM
├── docker-compose.yml          # Full local stack
├── .env.example                # Environment variable template
└── README.md
```

---

## Getting Started

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java JDK | 21+ | [sdkman.io](https://sdkman.io/) — `sdk install java 21-tem` |
| Maven | 3.9+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| Docker Desktop | Latest | [docker.com](https://www.docker.com/products/docker-desktop/) |
| Node.js | 20+ | [nodejs.org](https://nodejs.org/) |

### 1. Clone

```bash
git clone https://github.com/your-org/pulsepay.git
cd pulsepay
```

### 2. Configure environment

```bash
cp .env.example .env
# All defaults work for local dev — no changes needed to get started
```

### 3. Start the infrastructure stack

```bash
# Starts PostgreSQL, Redis, MongoDB, Elasticsearch, Kafka, Schema Registry,
# Zookeeper, Zipkin, Prometheus, and Grafana
docker-compose up -d

# Verify all containers are healthy
docker-compose ps
```

### 4. Build shared libraries

```bash
mvn clean install -pl libs/common-lib,libs/security-lib,libs/kafka-lib,libs/observability-lib
```

### 5. Start the services

```bash
# Start Config Server first — other services fetch config on startup
mvn spring-boot:run -pl services/config-server

# Then start remaining services (separate terminals, or use the helper script)
mvn spring-boot:run -pl apps/api-gateway
mvn spring-boot:run -pl services/auth-service
mvn spring-boot:run -pl services/payment-service
mvn spring-boot:run -pl services/wallet-service
mvn spring-boot:run -pl services/user-service
mvn spring-boot:run -pl services/fraud-detection
mvn spring-boot:run -pl services/notification-service

# Or start everything at once
./scripts/start-all.sh
```

### 6. Verify

```bash
curl http://localhost:8080/actuator/health        # Gateway health
open http://localhost:8761                        # Eureka dashboard
open http://localhost:8080/swagger-ui.html        # API docs
open http://localhost:3000                        # Grafana (admin / admin)
open http://localhost:9411                        # Zipkin traces
open http://localhost:5601                        # Kibana logs
```

---

## Service Port Reference

| Service | Port | Notes |
|---------|------|-------|
| API Gateway | `8080` | All client traffic enters here |
| Config Server | `8888` | Serves config to all services on startup |
| Eureka | `8761` | Service registry dashboard |
| Auth Service | `8081` | `/auth/**` |
| Payment Service | `8082` | `/payments/**` |
| Wallet Service | `8083` | `/wallets/**` |
| User Service | `8084` | `/users/**` |
| Fraud Detection | `8085` | Internal — Kafka consumer only |
| Notification Service | `8086` | Internal — Kafka consumer only |
| Report Service | `8087` | `/reports/**` |
| Audit Service | `8088` | Internal — append-only |
| PostgreSQL | `5432` | — |
| Redis | `6379` | — |
| MongoDB | `27017` | — |
| Elasticsearch | `9200` | — |
| Kafka | `9092` | Broker |
| Schema Registry | `8089` | Avro schema API |
| Zipkin | `9411` | Trace UI |
| Prometheus | `9090` | Metrics |
| Grafana | `3000` | Dashboards |
| Kibana | `5601` | Log search |

---

## Kafka Topics

| Topic | Produced By | Consumed By | Schema |
|-------|-------------|-------------|--------|
| `payment.events` | Payment Service | Wallet, Fraud, Audit | `PaymentEvent.avsc` |
| `wallet.events` | Wallet Service | Notification, Audit | `WalletEvent.avsc` |
| `fraud.alerts` | Fraud Detection | Notification, Payment, Audit | `FraudAlert.avsc` |
| `notification.requests` | Payment, Wallet, Fraud | Notification Service | `NotificationRequest.avsc` |
| `audit.events` | All services | Audit Service | `AuditEvent.avsc` |

All Avro schemas live in `libs/common-lib/src/main/avro/` and are registered in Schema Registry on startup.

---

## Running Tests

```bash
# Unit tests — all modules
mvn test

# Integration tests (Testcontainers — starts real Docker containers)
mvn verify -P integration-tests

# Single service
mvn test -pl services/payment-service

# Coverage report
mvn verify -P coverage
open services/payment-service/target/site/jacoco/index.html
```

Coverage requirement: **80% minimum** on all services. PRs that drop below this threshold are blocked in CI.

---

## Deployment

### Local / demo

```bash
docker-compose up -d
```

### Kubernetes via Helm

```bash
kubectl create secret generic pulsepay-secrets --from-env-file=.env.k8s -n pulsepay

helm upgrade --install pulsepay ./infra/k8s/helm/pulsepay \
  --namespace pulsepay \
  --values ./infra/k8s/helm/values.staging.yaml

kubectl rollout status deployment/payment-service -n pulsepay
```

### AWS via Terraform

```bash
cd infra/terraform
terraform init
terraform plan  -var-file=environments/staging.tfvars
terraform apply -var-file=environments/staging.tfvars
```

---

## API Quick Start

```bash
# 1. Authenticate
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"demo@pulsepay.in","password":"demo1234"}' \
  | jq -r '.access_token')

# 2. Initiate a UPI payment
curl -X POST http://localhost:8080/payments/initiate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.00,
    "currency": "INR",
    "rail": "UPI",
    "vpa": "merchant@okaxis",
    "idempotencyKey": "txn-demo-001",
    "description": "Order #1042"
  }'

# 3. Check wallet balance
curl http://localhost:8080/wallets/balance \
  -H "Authorization: Bearer $TOKEN"
```

Full OpenAPI 3 specs: `docs/api/` — or browse Swagger UI at `http://localhost:8080/swagger-ui.html`.

---

## Observability

### Grafana dashboards (provisioned automatically on startup)

| Dashboard | What it shows |
|-----------|---------------|
| Payment Throughput | TPS, success rate, p50/p95/p99 latency by rail |
| Kafka Consumer Health | Consumer lag per topic and partition, offset commit rate |
| Fraud Detection | Rules triggered per minute, alert rate, blocked transactions |
| JVM Health | Heap usage, GC pause time, thread count per service |
| Infrastructure | Pod CPU/memory, HPA scale events, node health |

### Distributed traces

Every request carries `X-B3-TraceId` propagated through Gateway → Service → Kafka message headers:

```
Gateway (2ms)
  └── payment-service (47ms)
        ├── PostgreSQL write (8ms)
        └── Kafka publish (3ms)
              ├── fraud-detection     [async] (11ms)
              ├── wallet-service      [async] (9ms)
              └── notification-service [async] (2ms)
```

---

## Key Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_HOST` | Yes | PostgreSQL host |
| `DB_PASSWORD` | Yes | PostgreSQL password — use Secrets Manager in prod |
| `REDIS_HOST` | Yes | Redis host |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | Kafka broker addresses |
| `SCHEMA_REGISTRY_URL` | Yes | Confluent Schema Registry URL |
| `JWT_SECRET` | Yes | HS256 signing secret — minimum 256-bit |
| `UPI_API_KEY` | Yes | NPCI UPI API credential |
| `STRIPE_SECRET_KEY` | Yes | Stripe secret key for card processing |
| `AWS_REGION` | Prod only | AWS region (MSK, RDS, ElastiCache) |

See `.env.example` for the full reference with descriptions and defaults.

---

## Security & Compliance

- **PCI-DSS**: Card numbers are never stored. All card data is tokenised via the payment rail provider before any persistence.
- **PII protection**: Aadhaar references and sensitive PII are tokenised at ingestion. Raw PII does not appear in logs, Kafka messages, or application databases.
- **Secrets management**: No secrets in source code or Docker images. Use `.env` locally; AWS Secrets Manager in production.
- **Audit trail**: Every state-changing operation publishes to `audit.events`. The Audit Service writes to MongoDB — append-only and immutable.

---

## Contributing

1. Fork the repository and create a branch — `git checkout -b feature/your-feature`
2. Follow the coding standards in `docs/architecture/coding-standards.md`
3. Write unit and integration tests — coverage must not drop below **80%**
4. Ensure `mvn verify` passes locally before opening a PR
5. Open a pull request against `develop`

```
main        ← production releases only
develop     ← integration branch
feature/*   ← new features
fix/*       ← bug fixes
hotfix/*    ← production hotfixes
```

---

## License

MIT — see [LICENSE](LICENSE) for details.

---

<div align="center">

Built with Spring Boot · Kafka · Java 21

*PulsePay Engineering Team — March 2026*

</div>
