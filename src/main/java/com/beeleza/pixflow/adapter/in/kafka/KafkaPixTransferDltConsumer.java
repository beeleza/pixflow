package com.beeleza.pixflow.adapter.in.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class KafkaPixTransferDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPixTransferDltConsumer.class);

    @KafkaListener(
            topics = "pix-transaction-response.DLT",
            groupId = "pixflow-settlement-dlt",
            containerFactory = "dltListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record    ) {
        String exceptionClass = header(record, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String exceptionMessage = header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        Header offsetHeader = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET);
        Long originalOffset = offsetHeader == null ? null : ByteBuffer.wrap(offsetHeader.value()).getLong();

        log.error(
                "PIX response sent to DLT. key={}, originalOffSet={}, exception={}, message={}, payload={}",
                record.key(), originalOffset, exceptionClass, exceptionMessage, record.value()
        );
    }

    public static String header(ConsumerRecord<?,?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
