package com.jaypay.remittance.adapter.in.web;

import com.jaypay.remittance.domain.Receiver;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceStatus;

public record RemittanceDetail(
        String remittanceId,
        String requestId,
        String fromMembershipId,
        int remittanceType,
        String toMembershipId,
        String toBankName,
        String toBankAccountNumber,
        long amount,
        RemittanceStatus remittanceStatus
) {

    static RemittanceDetail of(Remittance remittance) {
        var request = remittance.request();
        String id = remittance.remittanceId().toString();
        return switch (request.receiver()) {
            case Receiver.Member member -> new RemittanceDetail(id, request.requestId().value(), request.fromMembershipId(), 0,
                    member.membershipId(), null, null, request.amount(), remittance.status());
            case Receiver.BankAccount account -> new RemittanceDetail(id, request.requestId().value(), request.fromMembershipId(), 1,
                    null, account.bankName(), account.bankAccountNumber(), request.amount(), remittance.status());
        };
    }
}
