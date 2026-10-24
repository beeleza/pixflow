package com.beeleza.pixflow.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads a topic from the beginning with a throwaway consumer group, so a test sees every record
 * no matter when it subscribed. Records are matched by key because the topic is shared across tests.
 */
public final class KafkaTopicReader implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaTopicReader(KafkaProperties properties, String topic) {
        Map<String, Object> config = properties.buildConsumerProperties();
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        this.consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer());
        this.consumer.subscribe(List.of(topic));
    }

    public ConsumerRecord<String, String> awaitRecord(String key, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No record with key " + key + " within " + timeout);
    }

    @Override
    public void close() {
        consumer.close();
    }
}
