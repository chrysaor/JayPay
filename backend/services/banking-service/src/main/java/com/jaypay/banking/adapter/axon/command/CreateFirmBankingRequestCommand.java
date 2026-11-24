package com.jaypay.banking.adapter.axon.command;

public record CreateFirmBankingRequestCommand(
        String fromBankName,
        String fromBankAccountNumber,
        String toBankName,
        String toBankAccountNumber,
        int moneyAmount
) {
}
