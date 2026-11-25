package com.jaypay.banking.adapter.axon.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UpdateFirmBankingRequestCommand(
        @TargetAggregateIdentifier String aggregateIdentifier,
        int firmbankingStatus
) {
}
