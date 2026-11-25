package com.jaypay.remittance.domain;

public enum RemittanceStatus {
    REQUESTED,
    DEBITED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    REFUNDING,
    REFUNDED
}
