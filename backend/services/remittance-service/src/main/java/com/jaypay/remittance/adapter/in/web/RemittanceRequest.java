package com.jaypay.remittance.adapter.in.web;

import java.math.BigDecimal;

public record RemittanceRequest(
        String requestId,
        String fromMembershipId,
        Integer remittanceType,
        String toMembershipId,
        String toBankName,
        String toBankAccountNumber,
        BigDecimal amount
) {
}
