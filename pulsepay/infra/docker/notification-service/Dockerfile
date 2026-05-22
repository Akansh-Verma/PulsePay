# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY target/notification-service-1.0.0-SNAPSHOT.jar app.jar

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
