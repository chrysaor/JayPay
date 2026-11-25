package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.RecordedTransfer;
import com.jaypay.banking.domain.TransferStatus;

public interface BankTransferPort {

    void claim(BankTransfer transfer, TransferStatus status);

    RecordedTransfer findForUpdate(String requestId);

    void settle(String requestId, TransferStatus status);
}
