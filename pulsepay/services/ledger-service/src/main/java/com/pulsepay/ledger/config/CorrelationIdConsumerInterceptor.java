package com.pulsepay.ledger.config;

import com.pulsepay.common.config.KafkaHeaderConstants;
import com.pulsepay.common.util.CorrelationIdUtils;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CorrelationIdConsumerInterceptor implements ConsumerInterceptor<String, Object> {

    @Override
    public ConsumerRecords<String, Object> onConsume(ConsumerRecords<String, Object> records) {
        records.forEach(record -> {
            Header header = record.headers().lastHeader(KafkaHeaderConstants.CORRELATION_ID_HEADER);
            String correlationId;
            if (header != null) {
                correlationId = new String(header.value(), StandardCharsets.UTF_8);
            } else {
                correlationId = CorrelationIdUtils.generate();
            }
            MDC.put("correlationId", correlationId);
        });
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        MDC.remove("correlationId");
    }

    @Override
    public void close() {}

    @Override
    public void configure(Map<String, ?> configs) {}
}
