package com.jaypay.settlement.application.port.out;

import java.util.Optional;

public interface BankingPort {

    Optional<BankAccount> getRegisteredBankAccount(String membershipId);

    boolean requestFirmBanking(BankAccount to, long amount);
}
