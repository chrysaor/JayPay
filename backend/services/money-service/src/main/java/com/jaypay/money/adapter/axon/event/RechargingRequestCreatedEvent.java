package com.jaypay.money.adapter.axon.event;

public record RechargingRequestCreatedEvent(
        String rechargingRequestId,
        String membershipId,
        long amount,
        String registeredBankAccountAggregateIdentifier,
        String bankName,
        String bankAccountNumber
) {

    @Override
    public String toString() {
        return "RechargingRequestCreatedEvent{rechargingRequestId='" + rechargingRequestId + "', membershipId='" + membershipId
                + "', amount=" + amount + ", bankName='" + bankName + "'}";
    }
}
