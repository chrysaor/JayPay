package com.jaypay.banking.application.port.in;

import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.RecordedTransfer;
import com.jaypay.banking.domain.TransferStatus;

public interface TransferMoneyUseCase {

    RecordedTransfer transfer(BankTransfer transfer);

    TransferStatus reverse(String requestId, String reversalRequestId);
}
