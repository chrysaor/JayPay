package com.jaypay.settlement.application.port.out;

public record Payment(long paymentId, String franchiseId, long payout) {

    public String settlementId() {
        return "settlement-" + paymentId;
    }
}
