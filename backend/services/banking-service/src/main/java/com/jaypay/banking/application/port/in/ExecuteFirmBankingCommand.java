package com.jaypay.banking.application.port.in;

import com.jaypay.banking.domain.FirmBankingTransfer;

public record ExecuteFirmBankingCommand(
        String aggregateIdentifier,
        FirmBankingTransfer recordedTransfer,
        FirmBankingTransfer requestedTransfer
) {
}
