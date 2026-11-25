package com.jaypay.payment.adapter.in.web;

import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentDetail(
        long paymentId,
        String requestId,
        String requestMembershipId,
        String franchiseId,
        long requestPrice,
        int feeRateBasisPoints,
        long fee,
        long payout,
        PaymentStatus paymentStatus,
        Instant approvedAt
) {

    static PaymentDetail of(Payment payment) {
        return new PaymentDetail(
                payment.paymentId(),
                payment.requestId().value(),
                payment.requestMembershipId(),
                payment.franchiseId(),
                payment.requestPrice(),
                payment.feeRate().basisPoints(),
                payment.charge().fee(),
                payment.charge().payout(),
                payment.paymentStatus(),
                payment.approvedAt());
    }
}
