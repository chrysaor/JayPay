package com.jaypay.remittance.domain;

public class BankAccountNotConfirmedException extends RuntimeException {

    public BankAccountNotConfirmedException(String answer) {
        super("banking-service did not confirm the bank account: " + answer);
    }
}
