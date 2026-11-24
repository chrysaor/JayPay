package com.jaypay.settlement.application.port.out;

public record Payment(long paymentId, String franchiseId, int requestPrice, String franchiseFeeRate) {
}
