package com.jaypay.money.domain;

public record MoneyAmount(long value) {

    public MoneyAmount {
        if (value <= 0) {
            throw new IllegalArgumentException("A money amount is positive");
        }
    }
}
