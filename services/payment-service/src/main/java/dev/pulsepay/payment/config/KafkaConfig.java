package dev.pulsepay.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.pulsepay.payment.event.PaymentCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ProducerFactory<String, PaymentCreatedEvent> paymentProducerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper kafkaObjectMapper) {

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        addKafkaClientProperties(configProps, kafkaProperties);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        JsonSerializer<PaymentCreatedEvent> valueSerializer =
                new JsonSerializer<>(kafkaObjectMapper);

        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate(
            ProducerFactory<String, PaymentCreatedEvent> paymentProducerFactory) {
        return new KafkaTemplate<>(paymentProducerFactory);
    }

    private void addKafkaClientProperties(
            Map<String, Object> configProps,
            KafkaProperties kafkaProperties) {
        kafkaProperties.getProperties().forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                configProps.put(key, value);
            }
        });
    }
}
