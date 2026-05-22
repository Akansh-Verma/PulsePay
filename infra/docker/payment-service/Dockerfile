# Build context: repository root (PulsePay/).
# Uses infra/docker/reactor-pom.xml so unrelated service modules are not loaded in Docker.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY infra/docker/reactor-pom.xml pom.xml
COPY services/payment-service services/payment-service
COPY services/ledger-service services/ledger-service
RUN mvn -B -q -pl services/payment-service package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/services/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
