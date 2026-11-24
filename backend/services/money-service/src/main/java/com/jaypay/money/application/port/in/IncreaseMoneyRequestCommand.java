package com.jaypay.money.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class IncreaseMoneyRequestCommand extends SelfValidating<IncreaseMoneyRequestCommand> {

    @NotNull
    private final String targetMembershipId;

    @NotNull
    private final int amount;

    public IncreaseMoneyRequestCommand(String membershipId, int amount) {
        this.targetMembershipId = membershipId;
        this.amount = amount;
        this.validateSelf();
    }
}