package com.jaypay.money.adapter.axon.command;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.axonframework.modelling.command.TargetAggregateIdentifier;


@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class RechargingMoneyRequestCreateCommand extends SelfValidating<MemberMoneyCreatedCommand> {

    @TargetAggregateIdentifier
    private String aggregateIdentifier;
    private String rechargingRequestId;
    private String membershipId;
    private int amount;

    public RechargingMoneyRequestCreateCommand(String aggregateIdentifier, String rechargingRequestId, String membershipId, int amount) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.rechargingRequestId = rechargingRequestId;
        this.membershipId = membershipId;
        this.amount = amount;

        validateSelf();
    }

    public RechargingMoneyRequestCreateCommand() {
    }

}
