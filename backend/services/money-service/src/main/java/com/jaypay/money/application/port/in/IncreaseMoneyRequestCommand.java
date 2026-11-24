package com.jaypay.money.application.port.in;

import java.util.Objects;

public record IncreaseMoneyRequestCommand(String targetMembershipId, int amount) {

    public IncreaseMoneyRequestCommand {
        Objects.requireNonNull(targetMembershipId, "targetMembershipId");
    }
}
