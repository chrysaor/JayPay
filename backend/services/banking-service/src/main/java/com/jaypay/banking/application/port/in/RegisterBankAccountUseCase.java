package com.jaypay.banking.application.port.in;


import com.jaypay.banking.domain.RegisteredBankAccount;

public interface RegisterBankAccountUseCase {

    RegisteredBankAccount registerBankAccount(RegisterBankAccountCommand command);
    void registerBankAccountByEvent(RegisterBankAccountCommand command);

}