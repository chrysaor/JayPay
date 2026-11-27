package com.jaypay.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RollbackFirmBankingFinishedEvent {

    public static final int UNCONFIRMED = 0;
    public static final int CONFIRMED = 1;
    public static final int REFUSED = 2;

    private String rollbackFirmBankingId;
    private String membershipId;
    private String rollbackFirmbankingAggregateIdentifier;
    private int refundStatus;
}
