package com.jaypay.money.adapter.axon.event;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberMoneyCreatedEvent extends SelfValidating<MemberMoneyCreatedEvent> {

    @NotNull
    private String membershipId;

    public MemberMoneyCreatedEvent(@NotNull String membershipId) {
        this.membershipId = membershipId;
        this.validateSelf();
    }

    public MemberMoneyCreatedEvent() {
    }

}