package com.beeleza.pixflow.adapter.in.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KafkaPixTransferDltConsumerTest {

    @Test
    void readsHeaderAsUtf8Text() {
        ConsumerRecord<String, String> record = record();
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "transação inválida".getBytes(StandardCharsets.UTF_8));

        assertThat(KafkaPixTransferDltConsumer.header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                .isEqualTo("transação inválida");
    }

    @Test
    void readsLastValueOfRepeatedHeader() {
        ConsumerRecord<String, String> record = record();
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN, "first".getBytes(StandardCharsets.UTF_8));
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN, "last".getBytes(StandardCharsets.UTF_8));

        assertThat(KafkaPixTransferDltConsumer.header(record, KafkaHeaders.DLT_EXCEPTION_FQCN)).isEqualTo("last");
    }

    @Test
    void returnsNullForMissingHeader() {
        assertThat(KafkaPixTransferDltConsumer.header(record(), KafkaHeaders.DLT_EXCEPTION_FQCN)).isNull();
    }

    @Test
    void logsRecordWithOrWithoutDltHeaders() {
        KafkaPixTransferDltConsumer consumer = new KafkaPixTransferDltConsumer();
        ConsumerRecord<String, String> withHeaders = record();
        withHeaders.headers().add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.BYTES).putLong(42L).array());

        assertThatCode(() -> consumer.consume(record())).doesNotThrowAnyException();
        assertThatCode(() -> consumer.consume(withHeaders)).doesNotThrowAnyException();
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("pix-transaction-response.DLT", 0, 0L, "tx-1", "{}");
    }
}
