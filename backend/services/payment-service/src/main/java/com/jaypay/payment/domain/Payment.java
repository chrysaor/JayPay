package com.jaypay.payment.domain;

import java.time.Instant;

public record Payment(
        long paymentId,
        RequestId requestId,
        String requestMembershipId,
        String franchiseId,
        long requestPrice,
        FeeRate feeRate,
        Charge charge,
        PaymentStatus paymentStatus,
        Instant approvedAt
) {

    public boolean sameRequestAs(NewPayment other) {
        return requestMembershipId.equals(other.requestMembershipId())
                && franchiseId.equals(other.franchiseId())
                && requestPrice == other.requestPrice();
    }

    public String debitRequestId() {
        return "payment-" + paymentId + "-decrease";
    }
}
