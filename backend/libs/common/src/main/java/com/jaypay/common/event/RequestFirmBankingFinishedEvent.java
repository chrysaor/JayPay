package com.jaypay.common.event;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestFirmBankingFinishedEvent {

    public static final int SUCCEEDED = 0;
    public static final int REFUSED = 1;
    public static final int UNCONFIRMED = 2;

    private String requestFirmBankingId;
    private String rechargingRequestId;
    private String membershipId;
    private String toBankName;
    @ToString.Exclude
    private String toBankAccountNumber;
    private long moneyAmount;
    private int status;
    private String requestFirmbankingAggregateIdentifier;

    public boolean succeeded() {
        return status == SUCCEEDED;
    }
}
