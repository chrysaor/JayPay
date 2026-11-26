package com.jaypay.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.axonframework.modelling.command.TargetAggregateIdentifier;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestFirmBankingCommand {
    private String requestFirmBankingId;
    @TargetAggregateIdentifier
    private String aggregateIdentifier;
    private String rechargeRequestId;
    private String membershipId;
    private String fromBankName;
    @ToString.Exclude
    private String fromBankAccountNumber;
    private String toBankName;
    @ToString.Exclude
    private String toBankAccountNumber;
    private long moneyAmount;
}