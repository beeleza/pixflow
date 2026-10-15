package com.beeleza.pixflow.adapter.in.web.dto;

import com.beeleza.pixflow.domain.model.PixKeyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePixTransferRequest(
        @NotBlank @Size(max = 255)
        String idempotencyKey,

        @NotNull
        UUID sourceAccountId,

        @NotBlank @Size(max = 255)
        String destinationKey,

        @NotNull
        PixKeyType destinationKeyType,

        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount
) {
}
