package com.jaypay.banking.domain;

public record RecordedTransfer(BankTransfer transfer, TransferStatus status) {

    public boolean isFinal() {
        return status != TransferStatus.PENDING;
    }

    public boolean succeeded() {
        return status == TransferStatus.SUCCEEDED;
    }
}
