package com.beeleza.pixflow.adapter.in.web;

import com.beeleza.pixflow.adapter.in.web.dto.CreatePixTransferRequest;
import com.beeleza.pixflow.adapter.in.web.dto.PixTransactionPageResponse;
import com.beeleza.pixflow.adapter.in.web.dto.PixTransactionResponse;
import com.beeleza.pixflow.application.port.in.GetPixTransactionUseCase;
import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase;
import com.beeleza.pixflow.application.port.in.ProcessPixTransferUseCase;
import com.beeleza.pixflow.domain.model.PixTransaction;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pix")
public class PixTransactionController {

    private final ProcessPixTransferUseCase processPixTransferUseCase;
    private final ListPixTransactionsUseCase listPixTransactionsUseCase;
    private final GetPixTransactionUseCase getPixTransactionUseCase;

    public PixTransactionController(ProcessPixTransferUseCase processPixTransferUseCase,
                                    ListPixTransactionsUseCase listPixTransactionsUseCase,
                                    GetPixTransactionUseCase getPixTransactionUseCase) {
        this.processPixTransferUseCase = processPixTransferUseCase;
        this.listPixTransactionsUseCase = listPixTransactionsUseCase;
        this.getPixTransactionUseCase = getPixTransactionUseCase;
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

        return ResponseEntity.status(HttpStatus.CREATED).body(PixTransactionResponse.from(transaction));
    }

    @GetMapping
    public PixTransactionPageResponse list(@RequestParam UUID sourceAccountId,
                                           @RequestParam(required = false) TransactionStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PixTransactionPageResponse.from(listPixTransactionsUseCase.list(
                new ListPixTransactionsUseCase.Query(sourceAccountId, status, page, size)));
    }

    @GetMapping("/{id}")
    public PixTransactionResponse get(@PathVariable UUID id) {
        return PixTransactionResponse.from(getPixTransactionUseCase.get(id));
    }
}
