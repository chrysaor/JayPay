package com.jaypay.banking.application.port.in;

import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.RecordedTransfer;

import java.util.Optional;

public interface TransferMoneyUseCase {

    RecordedTransfer transfer(BankTransfer transfer);

    Optional<RecordedTransfer> reverse(String requestId, String reversalRequestId);
}
