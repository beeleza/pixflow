package com.beeleza.pixflow.adapter.out.kafka;

import com.beeleza.pixflow.adapter.in.kafka.PixTransactionResponseMessage;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
public class KafkaConfig {

    private final KafkaProperties properties;

    public KafkaConfig(KafkaProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ProducerFactory<String, PixTransactionMessage> pixTransactionProducerFactory() {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, PixTransactionMessage> pixTransactionKafkaTemplate() {
        return new KafkaTemplate<>(pixTransactionProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, PixTransactionResponseMessage> pixTransactionConsumerFactory() {
        DefaultKafkaConsumerFactory<String, PixTransactionResponseMessage> factory =
                new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties());
        factory.setKeyDeserializer(new org.apache.kafka.common.serialization.StringDeserializer());
        factory.setValueDeserializer(new JacksonJsonDeserializer<>(PixTransactionResponseMessage.class, false));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PixTransactionResponseMessage>
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PixTransactionResponseMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(pixTransactionConsumerFactory());
        return factory;
    }
}