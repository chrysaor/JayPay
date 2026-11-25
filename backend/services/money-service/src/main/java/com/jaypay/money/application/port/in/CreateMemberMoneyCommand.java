package com.jaypay.money.application.port.in;

import java.util.Objects;

public record CreateMemberMoneyCommand(String membershipId) {

    public CreateMemberMoneyCommand {
        Objects.requireNonNull(membershipId, "membershipId");
    }
}
