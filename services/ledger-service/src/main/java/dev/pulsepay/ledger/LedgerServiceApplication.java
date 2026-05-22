package dev.pulsepay.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * LedgerServiceApplication - Entry point for the Ledger Service.
 *
 * This is a minimal Spring Boot 3 microservice that consumes payment events from Kafka
 * and processes them into ledger entries (simulated without a database).
 */
@SpringBootApplication
@EnableKafka
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }

}
