package com.jaypay.payment.domain;

public record FeeRate(int basisPoints) {

    private static final int WHOLE = 10_000;

    public FeeRate {
        if (basisPoints < 0 || basisPoints > WHOLE) {
            throw new IllegalArgumentException("A fee rate is 0 to 10000 basis points");
        }
    }

    public Charge charge(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("An amount is positive");
        }
        long fee = amount / WHOLE * basisPoints + amount % WHOLE * basisPoints / WHOLE;
        return new Charge(fee, amount - fee);
    }
}
