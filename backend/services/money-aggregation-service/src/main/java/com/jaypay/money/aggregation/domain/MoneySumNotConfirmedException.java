package com.jaypay.money.aggregation.domain;

public class MoneySumNotConfirmedException extends RuntimeException {

    public MoneySumNotConfirmedException(String service, String answer) {
        super(service + " did not answer what the sum is read from: " + answer);
    }
}
