package com.jaypay.remittance.application.port.out;

public record NewRemittanceRequest(
        String fromMembershipId,
        String toMembershipId,
        String toBankName,
        String toBankAccountNumber,
        int remittanceType,
        int amount
) {
}
