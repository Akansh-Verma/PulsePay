Railway Docker build notes
=========================

Purpose
-------
This folder contains Dockerfiles for `payment-service` and `ledger-service` and a slim Maven reactor used during image builds.

Important Railway settings
-------------------------
- Builder type: Dockerfile
- Dockerfile path (payment): `infra/docker/payment-service/Dockerfile`
- Dockerfile path (ledger): `infra/docker/ledger-service/Dockerfile`
- Health check path: `/actuator/health`

Why this matters
-----------------
The Dockerfiles copy `infra/docker/reactor-pom.xml` (a slim aggregator) and the two service source trees into the build context, then run Maven to package the single service jar. This avoids building the full repo reactor and prevents dependency resolution failures for modules that are not needed in Phase 1 (for example `common-lib` and other services).

Railway gotchas
---------------
- Make sure Railway is configured to build using the Dockerfile path above. If Railway auto-detects a Maven build and runs `mvn` at the repo root (or uses `-Pproduction`), you may see build failures that do not occur in the Dockerfile build path.
- If your Railway build logs show `The requested profile "production" could not be activated because it does not exist`, set the Railway build command to not use `-Pproduction` or keep this repo's `production` profile (present to avoid warnings).

Recommended checks
------------------
1. In Railway, set Builder=Dockerfile and the Dockerfile path to the service Dockerfile before creating the service.
2. Add these env vars in Railway for both services (populate with Confluent credentials):

```env
SPRING_KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.region.provider.confluent.cloud:9092
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_SASL_MECHANISM=PLAIN
SPRING_KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="CONFLUENT_API_KEY" password="CONFLUENT_API_SECRET";
PULSEPAY_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example.com
```

3. Configure Railway healthcheck to call `/actuator/health`.

If you want, add a small CI job that performs the same Maven command the Dockerfile uses to catch regressions early:

```bash
mvn -f infra/docker/reactor-pom.xml -B -q -pl services/payment-service package -DskipTests
# and
mvn -f infra/docker/reactor-pom.xml -B -q -pl services/ledger-service package -DskipTests
```
