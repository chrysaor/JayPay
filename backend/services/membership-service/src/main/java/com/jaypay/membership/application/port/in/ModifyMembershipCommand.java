package com.jaypay.membership.application.port.in;

import lombok.Builder;

import java.util.Objects;

@Builder
public record ModifyMembershipCommand(String membershipId, String name, String email, String address, boolean isValid, boolean isCorp) {
    public ModifyMembershipCommand {
        Objects.requireNonNull(membershipId, "membershipId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        if (!isValid) {
            throw new IllegalArgumentException("a modified membership must stay valid");
        }
    }
}
