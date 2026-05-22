package dev.pulsepay.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.pulsepay.ledger.event.PaymentCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

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
    public ConsumerFactory<String, PaymentCreatedEvent> paymentEventConsumerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper kafkaObjectMapper) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        addKafkaClientProperties(props, kafkaProperties);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        JsonDeserializer<PaymentCreatedEvent> valueDeserializer =
                new JsonDeserializer<>(PaymentCreatedEvent.class, kafkaObjectMapper, false);
        valueDeserializer.addTrustedPackages("dev.pulsepay.ledger.event");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCreatedEvent>
            kafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentCreatedEvent> paymentEventConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentEventConsumerFactory);
        return factory;
    }

    private void addKafkaClientProperties(
            Map<String, Object> props,
            KafkaProperties kafkaProperties) {
        kafkaProperties.getProperties().forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                props.put(key, value);
            }
        });
    }
}
