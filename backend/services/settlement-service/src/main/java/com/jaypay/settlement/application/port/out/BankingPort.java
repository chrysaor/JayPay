package com.jaypay.settlement.application.port.out;

public interface BankingPort {
    BankAccount getRegisteredBankAccount(String membershipId);

    void requestFirmBanking(BankAccount to, int moneyAmount);
}
