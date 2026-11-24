package com.jaypay.money.application.port.out;

public record RegisteredBankAccount(String aggregateIdentifier, String bankName, String bankAccountNumber) {
}
