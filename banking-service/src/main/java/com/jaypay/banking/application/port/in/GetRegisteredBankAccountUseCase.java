package com.jaypay.banking.application.port.in;

import com.jaypay.banking.domain.RegisteredBankAccount;

public interface GetRegisteredBankAccountUseCase {

    RegisteredBankAccount getRegisteredBankAccount(GetRegisteredBankAccountCommand command);

}
