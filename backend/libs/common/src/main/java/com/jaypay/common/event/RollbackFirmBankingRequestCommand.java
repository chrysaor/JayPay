package com.jaypay.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RollbackFirmBankingRequestCommand {
    private String rollbackFirmbankingId;
    @TargetAggregateIdentifier
    private String aggregateIdentifier;
    private String rechargeRequestId;
    private String membershipId;
    private String bankName;
    @ToString.Exclude
    private String bankAccountNumber;
    private long moneyAmount;
}