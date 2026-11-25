package com.jaypay.remittance.application.port.out.banking;

public record BankingInfo(String bankName, String bankAccountNumber, boolean valid) {
}
