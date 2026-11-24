package com.jaypay.money.application.port.in;

public interface GetRegisteredBankAccountPort {

    RegisteredBankAccountAggregateIdentifier getRegisteredBankAccount(String membershipId);

}
