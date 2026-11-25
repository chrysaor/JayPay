package com.jaypay.money.adapter.axon.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RechargingMoneyRequestCreateCommand(
        @TargetAggregateIdentifier String aggregateIdentifier,
        String rechargingRequestId,
        String membershipId,
        int amount,
        String registeredBankAccountAggregateIdentifier,
        String bankName,
        String bankAccountNumber
) {
}
