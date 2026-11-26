package com.jaypay.payment.domain;

public record NewPayment(
        RequestId requestId,
        String requestMembershipId,
        String franchiseId,
        long requestPrice,
        FeeRate feeRate,
        Charge charge
) {

    public static NewPayment of(RequestId requestId, String requestMembershipId, String franchiseId, long requestPrice, FeeRate feeRate) {
        return new NewPayment(requestId, requestMembershipId, franchiseId, requestPrice, feeRate, feeRate.charge(requestPrice));
    }
}
