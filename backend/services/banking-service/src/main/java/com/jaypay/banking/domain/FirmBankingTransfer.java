package com.jaypay.banking.domain;

public record FirmBankingTransfer(
        String fromBankName,
        String fromBankAccountNumber,
        String toBankName,
        String toBankAccountNumber,
        int moneyAmount
) {
}
