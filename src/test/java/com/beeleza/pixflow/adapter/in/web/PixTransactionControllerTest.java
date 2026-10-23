package com.beeleza.pixflow.adapter.in.web;

import com.beeleza.pixflow.application.port.in.GetPixTransactionUseCase;
import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase;
import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.application.service.exception.IdempotencyKeyReuseException;
import com.beeleza.pixflow.application.service.exception.PixTransferInProgressException;
import com.beeleza.pixflow.application.service.exception.TransactionNotFoundException;
import com.beeleza.pixflow.domain.exception.IllegalTransactionStateException;
import com.beeleza.pixflow.domain.exception.InvalidPixKeyException;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PixTransactionController.class)
class PixTransactionControllerTest {

    private static final String VALID_BODY = """
            {
              "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
              "sourceAccountId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
              "destinationKey": "fulano@email.com",
              "destinationKeyType": "EMAIL",
              "amount": 150.50
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    private static final String SOURCE_ACCOUNT = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d";

    @MockitoBean
    private ProcessPixTransferUseCase processPixTransferUseCase;

    @MockitoBean
    private ListPixTransactionsUseCase listPixTransactionsUseCase;

    @MockitoBean
    private GetPixTransactionUseCase getPixTransactionUseCase;

    @Test
    void createsTransfer() throws Exception {
        given(processPixTransferUseCase.process(any())).willReturn(transaction());

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(150.50));
    }

    @Test
    void rejectsInvalidFieldsWithFieldErrors() throws Exception {
        String body = """
                {
                  "idempotencyKey": " ",
                  "sourceAccountId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                  "destinationKey": "fulano@email.com",
                  "destinationKeyType": "EMAIL",
                  "amount": 0
                }
                """;

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void rejectsMalformedBody() throws Exception {
        String body = VALID_BODY.replace("\"EMAIL\"", "\"FAX\"");

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsDomainExceptionToUnprocessableContent() throws Exception {
        given(processPixTransferUseCase.process(any()))
                .willThrow(new InvalidPixKeyException("value does not match the format of a EMAIL Pix key"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violated"));
    }

    @Test
    void mapsIdempotencyKeyReuseToUnprocessableContent() throws Exception {
        given(processPixTransferUseCase.process(any()))
                .willThrow(new IdempotencyKeyReuseException("Idempotency key was already used for a different transfer"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Idempotency key reused"));
    }

    @Test
    void mapsTransferInProgressToConflict() throws Exception {
        given(processPixTransferUseCase.process(any()))
                .willThrow(new PixTransferInProgressException("Transfer already being processed"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void passesRequestFieldsToUseCase() throws Exception {
        given(processPixTransferUseCase.process(any())).willReturn(transaction());

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        ArgumentCaptor<ProcessPixTransferUseCase.Command> command =
                ArgumentCaptor.forClass(ProcessPixTransferUseCase.Command.class);
        verify(processPixTransferUseCase).process(command.capture());
        assertThat(command.getValue()).isEqualTo(new ProcessPixTransferUseCase.Command(
                "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
                UUID.fromString(SOURCE_ACCOUNT),
                PixKeyType.EMAIL,
                "fulano@email.com",
                new BigDecimal("150.50")));
    }

    // IllegalTransactionStateException is also a DomainException; the more specific handler must win.
    @Test
    void mapsIllegalTransactionStateToConflict() throws Exception {
        given(processPixTransferUseCase.process(any()))
                .willThrow(new IllegalTransactionStateException("cannot move from FAILED to COMPLETED"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid transaction state"));
    }

    @Test
    void mapsDataIntegrityViolationToConflict() throws Exception {
        given(processPixTransferUseCase.process(any()))
                .willThrow(new DataIntegrityViolationException("uk_pix_transactions_idempotency_key"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void hidesUnexpectedErrorDetails() throws Exception {
        given(processPixTransferUseCase.process(any())).willThrow(new IllegalStateException("password=secret"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void getsTransactionById() throws Exception {
        PixTransaction transaction = transaction();
        given(getPixTransactionUseCase.get(transaction.id().value())).willReturn(transaction);

        mockMvc.perform(get("/api/v1/pix/{id}", transaction.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transaction.id().toString()))
                .andExpect(jsonPath("$.sourceAccount").value(SOURCE_ACCOUNT))
                .andExpect(jsonPath("$.destinationKeyType").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.settledAt").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        given(getPixTransactionUseCase.get(id)).willThrow(new TransactionNotFoundException("Transaction not found: " + id));

        mockMvc.perform(get("/api/v1/pix/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transaction not found"));
    }

    @Test
    void rejectsMalformedTransactionId() throws Exception {
        mockMvc.perform(get("/api/v1/pix/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getPixTransactionUseCase);
    }

    @Test
    void listsTransactionsOfAnAccount() throws Exception {
        given(listPixTransactionsUseCase.list(new ListPixTransactionsUseCase.Query(
                UUID.fromString(SOURCE_ACCOUNT), TransactionStatus.PENDING, 1, 5)))
                .willReturn(new ListPixTransactionsUseCase.PixTransactionPage(List.of(transaction()), 1, 5, 6));

        mockMvc.perform(get("/api/v1/pix")
                        .param("sourceAccountId", SOURCE_ACCOUNT)
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void usesDefaultPagingWhenOmitted() throws Exception {
        given(listPixTransactionsUseCase.list(any()))
                .willReturn(new ListPixTransactionsUseCase.PixTransactionPage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/pix").param("sourceAccountId", SOURCE_ACCOUNT))
                .andExpect(status().isOk());

        verify(listPixTransactionsUseCase).list(
                new ListPixTransactionsUseCase.Query(UUID.fromString(SOURCE_ACCOUNT), null, 0, 20));
    }

    @Test
    void requiresSourceAccountToList() throws Exception {
        mockMvc.perform(get("/api/v1/pix"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPageSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/api/v1/pix").param("sourceAccountId", SOURCE_ACCOUNT).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/pix").param("sourceAccountId", SOURCE_ACCOUNT).param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listPixTransactionsUseCase);
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/pix").param("sourceAccountId", SOURCE_ACCOUNT).param("status", "XPTO"))
                .andExpect(status().isBadRequest());
    }

    private static PixTransaction transaction() {
        return PixTransaction.initiate(
                IdempotencyKey.of("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"),
                new AccountId(UUID.fromString(SOURCE_ACCOUNT)),
                new PixKey("fulano@email.com", PixKeyType.EMAIL),
                new Money(new BigDecimal("150.50")));
    }
}
