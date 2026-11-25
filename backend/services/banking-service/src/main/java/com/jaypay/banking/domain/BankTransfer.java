package com.jaypay.banking.domain;

public record BankTransfer(
        String requestId,
        String fromBankName,
        String fromBankAccountNumber,
        String toBankName,
        String toBankAccountNumber,
        long amount
) {

    public static BankTransfer cancelled(String requestId) {
        return new BankTransfer(requestId, null, null, null, null, 0);
    }

    public BankTransfer reversed(String reversalRequestId) {
        return new BankTransfer(reversalRequestId, toBankName, toBankAccountNumber, fromBankName, fromBankAccountNumber, amount);
    }

    @Override
    public String toString() {
        return "BankTransfer{requestId='" + requestId + "', fromBankName='" + fromBankName + "', toBankName='" + toBankName
                + "', amount=" + amount + "}";
    }
}
