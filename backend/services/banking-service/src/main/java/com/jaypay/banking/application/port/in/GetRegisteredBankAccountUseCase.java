package com.jaypay.banking.application.port.in;

import com.jaypay.banking.domain.RegisteredBankAccount;

import java.util.Optional;

public interface GetRegisteredBankAccountUseCase {

    Optional<RegisteredBankAccount> getRegisteredBankAccount(GetRegisteredBankAccountCommand command);

}
