package com.jaypay.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.axonframework.modelling.command.TargetAggregateIdentifier;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckRegisteredBankAccountCommand {

    @TargetAggregateIdentifier
    private String aggregateIdentifier;
    private String rechargeRequestId;
    private String membershipId;
    private String checkRegisteredBankAccountId;
    private String bankName;
    @ToString.Exclude
    private String bankAccountNumber;
    private long amount;

}
