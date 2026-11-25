package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.BankTransfer;

public interface ExternalBankPort {

    BankAnswer send(BankTransfer transfer);
}
