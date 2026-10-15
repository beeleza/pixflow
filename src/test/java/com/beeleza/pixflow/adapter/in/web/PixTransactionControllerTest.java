package com.beeleza.pixflow.adapter.in.web;

import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.application.service.exception.PixTransferInProgressException;
import com.beeleza.pixflow.domain.exception.InvalidPixKeyException;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.IdempotencyKey;
import com.beeleza.pixflow.domain.model.Money;
import com.beeleza.pixflow.domain.model.PixKey;
import com.beeleza.pixflow.domain.model.PixKeyType;
import com.beeleza.pixflow.domain.model.PixTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @MockitoBean
    private ProcessPixTransferUseCase processPixTransferUseCase;

    @Test
    void createsTransfer() throws Exception {
        PixTransaction transaction = PixTransaction.initiate(
                IdempotencyKey.of("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"),
                new AccountId(UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")),
                new PixKey("fulano@email.com", PixKeyType.EMAIL),
                new Money(new BigDecimal("150.50")));
        given(processPixTransferUseCase.process(any())).willReturn(transaction);

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
    void mapsTransferInProgressToConflict() throws Exception {
        given(processPixTransferUseCase.process(any()))
                .willThrow(new PixTransferInProgressException("Transfer already being processed"));

        mockMvc.perform(post("/api/v1/pix").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }
}
