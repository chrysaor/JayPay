package com.jaypay.banking.adapter.out.axon;

import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.application.port.out.RegisteredBankAccountAggregatePort;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AxonCommandAdapter implements RegisteredBankAccountAggregatePort {

    private final CommandGateway commandGateway;

    @Override
    public CompletableFuture<String> createRegisteredBankAccountAggregate(String membershipId, String bankName, String bankAccountNumber) {
        return commandGateway.send(new CreateRegisteredBankAccountCommand(membershipId, bankName, bankAccountNumber));
    }
}
