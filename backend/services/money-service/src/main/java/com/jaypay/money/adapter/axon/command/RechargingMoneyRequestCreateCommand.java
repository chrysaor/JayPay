package com.jaypay.money.adapter.axon.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RechargingMoneyRequestCreateCommand(
        @TargetAggregateIdentifier String aggregateIdentifier,
        String rechargingRequestId,
        String membershipId,
        long amount,
        String registeredBankAccountAggregateIdentifier,
        String bankName,
        String bankAccountNumber
) {

    @Override
    public String toString() {
        return "RechargingMoneyRequestCreateCommand{aggregateIdentifier='" + aggregateIdentifier + "', rechargingRequestId='" + rechargingRequestId
                + "', membershipId='" + membershipId + "', amount=" + amount + ", bankName='" + bankName + "'}";
    }
}
