package com.beeleza.pixflow.adapter.out.kafka;

import com.beeleza.pixflow.adapter.in.kafka.PixTransactionResponseMessage;
import com.beeleza.pixflow.application.service.exception.TransactionMismatchException;
import com.beeleza.pixflow.domain.exception.DomainException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConfig {

    private final KafkaProperties properties;

    public KafkaConfig(KafkaProperties properties) {
        this.properties = properties;
    }

    // The outbox stores payloads already serialized as JSON, so they are sent as-is.
    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate() {
        var pf = new DefaultKafkaProducerFactory<String, String>(
                properties.buildProducerProperties(), new StringSerializer(), new StringSerializer());
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, PixTransactionResponseMessage> pixTransactionConsumerFactory() {
        DefaultKafkaConsumerFactory<String, PixTransactionResponseMessage> factory =
                new DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties());

        factory.setKeyDeserializer(new StringDeserializer());

        JacksonJsonDeserializer<PixTransactionResponseMessage> jsonDeserializer = new JacksonJsonDeserializer<>(PixTransactionResponseMessage.class, false);

        factory.setValueDeserializer(
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PixTransactionResponseMessage>
            kafkaListenerContainerFactory(DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PixTransactionResponseMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(pixTransactionConsumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate() {
        var serializer = new DelegatingByTypeSerializer(Map.of(
            byte[].class, new ByteArraySerializer(),
            PixTransactionResponseMessage.class, new JacksonJsonSerializer<>()
        ));
        var pf = new DefaultKafkaProducerFactory<String, Object>(
                properties.buildProducerProperties(), new StringSerializer(),serializer);
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
        // Negative partition: let the producer pick it, so the DLT does not need
        // as many partitions as the source topic.
        var recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", -1));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(DomainException.class, TransactionMismatchException.class);
        return handler;
    }

    @Bean
    public NewTopic pixTransactionResponseDlt() {
        return TopicBuilder.name("pix-transaction-response.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltListenerContainerFactory() {
        var cf = new DefaultKafkaConsumerFactory<String, String>(properties.buildConsumerProperties(), new StringDeserializer(), new StringDeserializer());
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();

        factory.setConsumerFactory(cf);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));

        return factory;
    }
}