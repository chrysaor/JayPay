package com.jaypay.settlement.port.out;

public interface GetRegisteredBankAccountPort {
    RegisteredBankAccountAggregateIdentifier getRegisteredBankAccount(String membershipId);

    void requestFirmbanking(String bankName, String bankAccountNumber, int moneyAmount);
}
