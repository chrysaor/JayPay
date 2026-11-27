package com.jaypay.remittance.domain;

public enum RemittanceStatus {
    REQUESTED,
    DEBITED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    REFUNDING,
    REFUNDED;

    public boolean finished() {
        return this == COMPLETED || this == FAILED || this == REFUNDED;
    }
}
