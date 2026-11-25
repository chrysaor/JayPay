package com.jaypay.remittance.domain;

public class RemittanceRejectedException extends RuntimeException {

    private final Reason reason;

    public RemittanceRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_SENDER,
        INVALID_RECEIVER,
        INVALID_BANK_ACCOUNT,
        REQUEST_ID_REUSED,
        REMITTANCE_NOT_CONFIRMED
    }
}
