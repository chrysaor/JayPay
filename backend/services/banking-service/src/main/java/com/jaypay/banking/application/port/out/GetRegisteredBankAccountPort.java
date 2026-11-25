package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.RegisteredBankAccount;

import java.util.Optional;

public interface GetRegisteredBankAccountPort {

    Optional<RegisteredBankAccount> getRegisteredBankAccount(String membershipId);

}
