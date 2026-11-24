package com.jaypay.banking.adapter.out.axon;

import com.jaypay.banking.adapter.axon.command.CreateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.axon.command.UpdateFirmBankingRequestCommand;
import com.jaypay.banking.application.port.out.FirmBankingAggregatePort;
import com.jaypay.banking.application.port.out.RegisteredBankAccountAggregatePort;
import com.jaypay.banking.domain.FirmBankingTransfer;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AxonCommandAdapter implements RegisteredBankAccountAggregatePort, FirmBankingAggregatePort {

    private final CommandGateway commandGateway;

    @Override
    public CompletableFuture<String> createRegisteredBankAccountAggregate(String membershipId, String bankName, String bankAccountNumber) {
        return commandGateway.send(new CreateRegisteredBankAccountCommand(membershipId, bankName, bankAccountNumber));
    }

    @Override
    public CompletableFuture<String> createFirmBankingAggregate(FirmBankingTransfer transfer) {
        return commandGateway.send(new CreateFirmBankingRequestCommand(
                transfer.fromBankName(),
                transfer.fromBankAccountNumber(),
                transfer.toBankName(),
                transfer.toBankAccountNumber(),
                transfer.moneyAmount()));
    }

    @Override
    public CompletableFuture<String> updateFirmBankingAggregate(String aggregateIdentifier, int firmBankingStatus) {
        return commandGateway.send(new UpdateFirmBankingRequestCommand(aggregateIdentifier, firmBankingStatus));
    }

}
