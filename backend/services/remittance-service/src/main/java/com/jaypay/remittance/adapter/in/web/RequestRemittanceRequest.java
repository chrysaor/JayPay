package com.jaypay.remittance.adapter.in.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestRemittanceRequest {

    private String fromMembershipId;
    private String toMembershipId;

    private String toBankName;
    private String toBankAccountNumber;

    private int remittanceType;
    private int amount;
}
