package com.jaypay.money.application.port.out;

import java.util.Optional;

public interface GetRegisteredBankAccountPort {

    Optional<RegisteredBankAccount> getRegisteredBankAccount(String membershipId);

}
