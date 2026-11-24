package com.jaypay.banking.adapter.axon.event;

public record CreateRegisteredBankAccountEvent(
        String membershipId,
        String bankName,
        String bankAccountNumber
) {
}
