package com.jaypay.money.adapter.axon.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class RechargingRequestCreatedEvent {
    // "Charge" event created

    private String rechargingRequestId;
    private String membershipId;
    private int amount;
    private String registeredBankAccountAggregateIdentifier;
    private String bankName;
    private String bankAccountNumber;

}
