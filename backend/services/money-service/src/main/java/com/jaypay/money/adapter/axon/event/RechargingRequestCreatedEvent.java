package com.jaypay.money.adapter.axon.event;

public record RechargingRequestCreatedEvent(
        String rechargingRequestId,
        String membershipId,
        int amount,
        String registeredBankAccountAggregateIdentifier,
        String bankName,
        String bankAccountNumber
) {
}
