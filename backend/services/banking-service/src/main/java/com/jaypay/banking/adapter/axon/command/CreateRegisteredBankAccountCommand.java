package com.jaypay.banking.adapter.axon.command;

public record CreateRegisteredBankAccountCommand(
        String membershipId,
        String bankName,
        String bankAccountNumber
) {
}
