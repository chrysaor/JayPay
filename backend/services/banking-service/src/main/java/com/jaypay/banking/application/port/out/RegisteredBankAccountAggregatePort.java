package com.jaypay.banking.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface RegisteredBankAccountAggregatePort {

    CompletableFuture<String> createRegisteredBankAccountAggregate(String membershipId, String bankName, String bankAccountNumber);

}
