# PulsePay Phase 1 Railway Deployment

This guide deploys the minimal backend MVP:

- `payment-service`: HTTP API and Kafka producer
- `ledger-service`: Kafka consumer with HTTP health endpoint
- Confluent Cloud Kafka topic: `payment.events`

No PostgreSQL, Redis, Kubernetes, Terraform, CI/CD, auth, or observability stack is required for this phase.

## Deployment Blockers Fixed

- `payment-service` and `ledger-service` now include Spring Boot Actuator.
- `ledger-service` now includes a web server so Railway can call `/actuator/health`.
- Both services read `SERVER_PORT`, then Railway's `PORT`, then local defaults.
- Both custom Kafka factories now pass through Confluent Cloud SASL/security properties.
- The frontend can call `payment-service` through configurable CORS origins.
- The full root Maven reactor includes unrelated modules that are not needed for Phase 1. Railway should build with the service Dockerfiles, which use `infra/docker/reactor-pom.xml` to load only `payment-service` and `ledger-service`.

## Docker Status

The Dockerfiles in `infra/docker/payment-service/Dockerfile` and `infra/docker/ledger-service/Dockerfile` are suitable for Railway:

- Java 21 build image: `maven:3.9.9-eclipse-temurin-21`
- Java 21 runtime image: `eclipse-temurin:21-jre-jammy`
- Multi-stage build: yes
- Startup command: `java -jar /app/app.jar`
- Spring Boot executable jars are created through the `spring-boot-maven-plugin` `repackage` goal.
- Build context: repository root
- Dockerfile paths:
  - `infra/docker/payment-service/Dockerfile`
  - `infra/docker/ledger-service/Dockerfile`

## Confluent Cloud Setup

1. Create a Confluent Cloud Kafka cluster.
2. Create a topic named `payment.events`.
3. Create a Kafka API key and secret for the cluster.
4. Copy the cluster bootstrap server from Confluent Cloud.
5. Use these values in both Railway backend services:

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.region.provider.confluent.cloud:9092
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=PLAIN
SPRING_KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="CONFLUENT_API_KEY" password="CONFLUENT_API_SECRET";
```

Replace `CONFLUENT_API_KEY` and `CONFLUENT_API_SECRET` with the real key and secret. Keep the quotes and trailing semicolon in `SPRING_KAFKA_SASL_JAAS_CONFIG`.

## Railway: payment-service

1. Create a new Railway project from the GitHub repo.
2. Add a service for `payment-service`.
3. Set the builder to Dockerfile.
4. Set Dockerfile path:

```text
infra/docker/payment-service/Dockerfile
```

5. Set environment variables:

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.region.provider.confluent.cloud:9092
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=PLAIN
SPRING_KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="CONFLUENT_API_KEY" password="CONFLUENT_API_SECRET";
PULSEPAY_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example.com
```

`SERVER_PORT` is optional on Railway because the app also reads Railway's `PORT`.

6. Configure Railway health check path:

```text
/actuator/health
```

7. Generate a public domain for the service. This URL is the backend API base URL.

## Railway: ledger-service

1. Add a second Railway service from the same GitHub repo.
2. Set the builder to Dockerfile.
3. Set Dockerfile path:

```text
infra/docker/ledger-service/Dockerfile
```

4. Set environment variables:

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.region.provider.confluent.cloud:9092
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=PLAIN
SPRING_KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="CONFLUENT_API_KEY" password="CONFLUENT_API_SECRET";
```

5. Configure Railway health check path:

```text
/actuator/health
```

6. A public domain is optional for `ledger-service`. It is useful for checking `/actuator/health`, but the service mainly runs as a Kafka consumer.

## Optional Frontend Variable

If deploying `pulsepay-frontend`, set:

```env
NEXT_PUBLIC_API_BASE_URL=https://your-payment-service.up.railway.app
```

Then update `payment-service`:

```env
PULSEPAY_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example.com
```

For multiple allowed origins, use a comma-separated list.

## Verification

Check service health:

```bash
curl https://your-payment-service.up.railway.app/actuator/health
curl https://your-ledger-service.up.railway.app/actuator/health
```

Expected response includes:

```json
{"status":"UP"}
```

Publish a test payment:

```bash
curl -X POST https://your-payment-service.up.railway.app/payments/test
```

Expected result:

1. `payment-service` returns HTTP `201` with a `paymentId`.
2. `payment-service` logs `event=payment_created_published`.
3. Confluent Cloud shows activity on `payment.events`.
4. `ledger-service` logs `event=payment_event_received`.
5. `ledger-service` logs `event=payment_event_processed_ok`.

## End-to-End Flow

```text
Frontend or curl
  -> payment-service POST /payments/test
  -> Confluent Cloud Kafka topic payment.events
  -> ledger-service Kafka consumer group ledger-group
  -> ledger-service logs processed event
```
