package com.jaypay.money.application.port.out;

public interface GetRegisteredBankAccountPort {

    RegisteredBankAccount getRegisteredBankAccount(String membershipId);

}
