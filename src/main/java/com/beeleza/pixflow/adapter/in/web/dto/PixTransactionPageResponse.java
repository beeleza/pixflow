package com.beeleza.pixflow.adapter.in.web.dto;

import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase.PixTransactionPage;

import java.util.List;

public record PixTransactionPageResponse(
        List<PixTransactionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PixTransactionPageResponse from(PixTransactionPage page) {
        return new PixTransactionPageResponse(
                page.items().stream().map(PixTransactionResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages()
        );
    }
}
