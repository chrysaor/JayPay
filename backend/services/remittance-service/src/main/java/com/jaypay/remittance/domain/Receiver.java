package com.jaypay.remittance.domain;

public sealed interface Receiver {

    record Member(String membershipId) implements Receiver {
    }

    record BankAccount(String bankName, String bankAccountNumber) implements Receiver {
    }
}
