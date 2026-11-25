package com.jaypay.money.application.port.in;

import com.jaypay.money.domain.MoneyAmount;
import com.jaypay.money.domain.RequestId;

import java.util.Objects;

public record ChangeMoneyCommand(RequestId requestId, String targetMembershipId, MoneyAmount amount) {

    public ChangeMoneyCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(targetMembershipId, "targetMembershipId");
        Objects.requireNonNull(amount, "amount");
    }
}
