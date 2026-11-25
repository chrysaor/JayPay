package com.jaypay.remittance.application.port.in;

import lombok.Builder;

import java.util.Objects;

@Builder
public record RequestRemittanceCommand(
        String fromMembershipId,
        String toMembershipId,
        String toBankName,
        String toBankAccountNumber,
        int remittanceType,
        int amount
) {
    public RequestRemittanceCommand {
        Objects.requireNonNull(fromMembershipId, "fromMembershipId");
    }
}
