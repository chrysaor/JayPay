package com.jaypay.banking.adapter.axon.event;

public record FirmBankingRequestCreatedEvent(
        String fromBankName,
        String fromBankAccountNumber,
        String toBankName,
        String toBankAccountNumber,
        int moneyAmount
) {
}
