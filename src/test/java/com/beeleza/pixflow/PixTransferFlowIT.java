package com.beeleza.pixflow;

import com.beeleza.pixflow.adapter.out.outbox.OutboxRelay;
import com.beeleza.pixflow.adapter.out.persistence.OutboxEventEntity;
import com.beeleza.pixflow.support.AbstractIntegrationTest;
import com.beeleza.pixflow.support.KafkaTopicReader;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives a transfer through HTTP, the outbox, Kafka and back, with the test playing the Pix network.
 */
class PixTransferFlowIT extends AbstractIntegrationTest {

    private static final String REQUEST_TOPIC = "pix-transaction";
    private static final String RESPONSE_TOPIC = "pix-transaction-response";
    private static final String DLT = "pix-transaction-response.DLT";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Test
    void completesTransferAcceptedByTheNetwork() throws Exception {
        String key = UUID.randomUUID().toString();
        String transactionId = createTransfer(key, "150.50");

        try (KafkaTopicReader requests = new KafkaTopicReader(kafkaProperties, REQUEST_TOPIC)) {
            outboxRelay.publishPending();

            String payload = requests.awaitRecord(transactionId, TIMEOUT).value();
            assertThat(JsonPath.<String>read(payload, "$.transactionId")).isEqualTo(transactionId);
            assertThat(JsonPath.<String>read(payload, "$.idempotencyKey")).isEqualTo(key);
            assertThat(JsonPath.<String>read(payload, "$.destinationKeyType")).isEqualTo("EMAIL");
        }
        assertThat(outboxEventRepository.findAll()).singleElement()
                .extracting(OutboxEventEntity::getPublishedAt).isNotNull();

        respond(transactionId, """
                {"transactionId": "%s", "idempotencyKey": "%s", "accepted": true}
                """.formatted(transactionId, key));

        await().atMost(TIMEOUT).untilAsserted(() -> mockMvc.perform(get("/api/v1/pix/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.settledAt").isNotEmpty()));
    }

    @Test
    void failsTransferRejectedByTheNetwork() throws Exception {
        String key = UUID.randomUUID().toString();
        String transactionId = createTransfer(key, "150.50");

        respond(transactionId, """
                {"transactionId": "%s", "idempotencyKey": "%s", "accepted": false, "failureReason": "INVALID_AMOUNT"}
                """.formatted(transactionId, key));

        await().atMost(TIMEOUT).untilAsserted(() -> mockMvc.perform(get("/api/v1/pix/{id}", transactionId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("INVALID_AMOUNT")));
    }

    @Test
    void repeatedRequestCreatesOneTransferAndOneEvent() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = createTransfer(key, "150.50");
        String second = createTransfer(key, "150.50");

        assertThat(second).isEqualTo(first);
        assertThat(pixTransactionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsIdempotencyKeyReusedForAnotherTransfer() throws Exception {
        String key = UUID.randomUUID().toString();
        createTransfer(key, "150.50");

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(body(key, "999.00")))
                .andExpect(status().isUnprocessableContent());

        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void sendsUnreadableResponseToDeadLetterTopic() {
        String messageKey = UUID.randomUUID().toString();

        try (KafkaTopicReader deadLetters = new KafkaTopicReader(kafkaProperties, DLT)) {
            respond(messageKey, "this is not json");

            ConsumerRecord<String, String> record = deadLetters.awaitRecord(messageKey, TIMEOUT);
            assertThat(record.value()).isEqualTo("this is not json");
            assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_FQCN)).contains("DeserializationException");
        }
    }

    @Test
    void sendsResponseForAnotherTransactionToDeadLetterTopicAndKeepsTransferPending() throws Exception {
        String key = UUID.randomUUID().toString();
        String transactionId = createTransfer(key, "150.50");
        String wrongId = UUID.randomUUID().toString();

        try (KafkaTopicReader deadLetters = new KafkaTopicReader(kafkaProperties, DLT)) {
            respond(wrongId, """
                    {"transactionId": "%s", "idempotencyKey": "%s", "accepted": true}
                    """.formatted(wrongId, key));

            // Exceptions thrown by the listener arrive wrapped in ListenerExecutionFailedException.
            ConsumerRecord<String, String> record = deadLetters.awaitRecord(wrongId, TIMEOUT);
            assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).endsWith("TransactionMismatchException");
        }
        mockMvc.perform(get("/api/v1/pix/{id}", transactionId)).andExpect(jsonPath("$.status").value("PENDING"));
    }

    // `accepted` is a primitive boolean: a missing field must be rejected, never read as false (= FAILED).
    @Test
    void sendsResponseWithoutAcceptedFlagToDeadLetterTopicAndKeepsTransferPending() throws Exception {
        String key = UUID.randomUUID().toString();
        String transactionId = createTransfer(key, "150.50");

        try (KafkaTopicReader deadLetters = new KafkaTopicReader(kafkaProperties, DLT)) {
            respond(transactionId, """
                    {"transactionId": "%s", "idempotencyKey": "%s"}
                    """.formatted(transactionId, key));

            ConsumerRecord<String, String> record = deadLetters.awaitRecord(transactionId, TIMEOUT);
            assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_FQCN)).contains("DeserializationException");
            assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).contains("\"accepted\"");
        }
        mockMvc.perform(get("/api/v1/pix/{id}", transactionId)).andExpect(jsonPath("$.status").value("PENDING"));
    }

    private String createTransfer(String idempotencyKey, String amount) throws Exception {
        String response = mockMvc.perform(post("/api/v1/pix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(idempotencyKey, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String body(String idempotencyKey, String amount) {
        return """
                {
                  "idempotencyKey": "%s",
                  "sourceAccountId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                  "destinationKey": "fulano@email.com",
                  "destinationKeyType": "EMAIL",
                  "amount": %s
                }
                """.formatted(idempotencyKey, amount);
    }

    private void respond(String messageKey, String payload) {
        kafkaTemplate.send(RESPONSE_TOPIC, messageKey, payload).join();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
