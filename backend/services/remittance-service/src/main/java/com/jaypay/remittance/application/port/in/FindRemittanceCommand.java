package com.jaypay.remittance.application.port.in;

import lombok.Builder;

import java.util.Objects;

@Builder
public record FindRemittanceCommand(String membershipId) {
    public FindRemittanceCommand {
        Objects.requireNonNull(membershipId, "membershipId");
    }
}
