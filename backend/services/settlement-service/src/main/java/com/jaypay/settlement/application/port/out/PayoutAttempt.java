package com.jaypay.settlement.application.port.out;

public record PayoutAttempt(long paymentId, int attempt, BankAccount account) {

    public String requestId() {
        return attempt == 1 ? "settlement-" + paymentId : "settlement-" + paymentId + "-" + attempt;
    }
}
