package com.jaypay.payment.domain;

public class PaymentRejectedException extends RuntimeException {

    private final Reason reason;

    public PaymentRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_PAYER,
        INVALID_MERCHANT,
        REQUEST_ID_REUSED,
        PAYMENT_NOT_FOUND,
        PAYMENT_NOT_PAID,
        PAYMENT_NOT_CONFIRMED
    }
}
