package com.beeleza.pixflow.adapter.in.web;

import com.beeleza.pixflow.adapter.in.web.dto.CreatePixTransferRequest;
import com.beeleza.pixflow.adapter.in.web.dto.PixTransactionResponse;
import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.domain.model.PixTransaction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pix")
public class PixTransactionController {

    private final ProcessPixTransferUseCase processPixTransferUseCase;

    public PixTransactionController(ProcessPixTransferUseCase processPixTransferUseCase) {
        this.processPixTransferUseCase = processPixTransferUseCase;
    }

    @PostMapping
    public ResponseEntity<PixTransactionResponse> create(@Valid @RequestBody CreatePixTransferRequest request) {
        PixTransaction transaction = processPixTransferUseCase.process(
                new ProcessPixTransferUseCase.Command(
                        request.idempotencyKey(),
                        request.sourceAccountId(),
                        request.destinationKeyType(),
                        request.destinationKey(),
                        request.amount()
                )
        );

        PixTransactionResponse response = new PixTransactionResponse(
                transaction.id().toString(),
                transaction.idempotencyKey().value(),
                transaction.sourceAccount().toString(),
                transaction.destinationKey().value(),
                transaction.destinationKey().type().name(),
                transaction.amount().amount(),
                transaction.createdAt(),
                transaction.status().name(),
                transaction.settledAt(),
                transaction.failureReason()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
