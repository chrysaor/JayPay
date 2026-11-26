package com.jaypay.banking.adapter.out.persistence;

import com.jaypay.banking.application.port.out.BankTransferPort;
import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.RecordedTransfer;
import com.jaypay.banking.domain.TransferStatus;
import com.jaypay.common.PersistenceAdapter;
import lombok.RequiredArgsConstructor;

@PersistenceAdapter
@RequiredArgsConstructor
public class BankTransferPersistenceAdapter implements BankTransferPort {

    private final SpringDataBankTransferRepository repository;

    @Override
    public void claim(BankTransfer transfer, TransferStatus status) {
        repository.claim(transfer.requestId(), transfer.fromBankName(), transfer.fromBankAccountNumber(),
                transfer.toBankName(), transfer.toBankAccountNumber(), transfer.amount(), status.name());
    }

    @Override
    public RecordedTransfer findForUpdate(String requestId) {
        BankTransferJpaEntity entity = repository.findForUpdate(requestId).orElseThrow();
        return new RecordedTransfer(new BankTransfer(entity.getRequestId(), entity.getFromBankName(), entity.getFromBankAccountNumber(),
                entity.getToBankName(), entity.getToBankAccountNumber(), entity.getAmount()), entity.getStatus());
    }

    @Override
    public void settle(String requestId, TransferStatus status) {
        repository.settle(requestId, status.name());
    }
}
