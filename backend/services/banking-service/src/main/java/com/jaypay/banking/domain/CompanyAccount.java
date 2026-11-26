package com.jaypay.banking.domain;

public record CompanyAccount(String bankName, String bankAccountNumber) {

    public BankTransfer receive(String requestId, String fromBankName, String fromBankAccountNumber, long amount) {
        return new BankTransfer(requestId, fromBankName, fromBankAccountNumber, bankName, bankAccountNumber, amount);
    }

    public BankTransfer send(String requestId, String toBankName, String toBankAccountNumber, long amount) {
        return new BankTransfer(requestId, bankName, bankAccountNumber, toBankName, toBankAccountNumber, amount);
    }

    public boolean sendsAgain(BankTransfer stored, BankTransfer asked) {
        return asked.equals(send(stored.requestId(), stored.toBankName(), stored.toBankAccountNumber(), stored.amount()));
    }

    @Override
    public String toString() {
        return "CompanyAccount{bankName='" + bankName + "'}";
    }
}
