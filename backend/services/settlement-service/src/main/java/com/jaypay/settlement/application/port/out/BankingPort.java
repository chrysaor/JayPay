package com.jaypay.settlement.application.port.out;

import java.util.Optional;

public interface BankingPort {

    Optional<BankAccount> getRegisteredBankAccount(String membershipId);

    Payout pay(String requestId, BankAccount to, long amount);
}
