package com.beeleza.pixflow.application.service;

import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase.PixTransactionPage;
import com.beeleza.pixflow.application.port.in.ListPixTransactionsUseCase.Query;
import com.beeleza.pixflow.application.port.out.LoadPixTransactionPort;
import com.beeleza.pixflow.domain.model.AccountId;
import com.beeleza.pixflow.domain.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ListPixTransactionsServiceTest {

    private final LoadPixTransactionPort load = mock(LoadPixTransactionPort.class);
    private final ListPixTransactionsService service = new ListPixTransactionsService(load);

    @Test
    void passesFiltersAndPagingToThePort() {
        UUID account = UUID.randomUUID();
        PixTransactionPage page = new PixTransactionPage(List.of(), 2, 10, 25);
        given(load.findBySourceAccount(new AccountId(account), TransactionStatus.FAILED, 2, 10)).willReturn(page);

        assertThat(service.list(new Query(account, TransactionStatus.FAILED, 2, 10))).isSameAs(page);
    }

    @Test
    void passesNullStatusToListEveryStatus() {
        UUID account = UUID.randomUUID();
        PixTransactionPage page = new PixTransactionPage(List.of(), 0, 20, 0);
        given(load.findBySourceAccount(new AccountId(account), null, 0, 20)).willReturn(page);

        assertThat(service.list(new Query(account, null, 0, 20))).isSameAs(page);
    }
}
