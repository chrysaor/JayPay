package com.jaypay.banking.adapter.in.axon;

import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.axon.event.CreateRegisteredBankAccountEvent;
import com.jaypay.banking.application.port.in.RequestBankingInfoCommand;
import com.jaypay.banking.application.port.in.RequestBankingInfoUseCase;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import com.jaypay.common.event.CheckedRegisteredBankAccountEvent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;


@Aggregate()
@Data
@Slf4j
public class RegisteredBankAccountAggregate {

    @AggregateIdentifier
    private String id;
    private String membershipId;
    private String bankName;
    private String bankAccountNumber;

    public RegisteredBankAccountAggregate() {}

    @CommandHandler
    public RegisteredBankAccountAggregate(CreateRegisteredBankAccountCommand command) {
        log.info("CreateRegisteredBankAccountCommand Sourcing Handler");

        apply(new CreateRegisteredBankAccountEvent(
                command.getMembershipId(), command.getBankName(), command.getBankAccountNumber()
        ));
    }

    @CommandHandler
    public void handle(CheckRegisteredBankAccountCommand command, RequestBankingInfoUseCase requestBankingInfoUseCase) {
        log.info("CheckRegisteredBankAccountCommand Handler");

        id = command.getAggregateIdentifier();

        boolean isValid = requestBankingInfoUseCase.requestExternalBankingInfo(
                new RequestBankingInfoCommand(command.getBankName(), command.getBankAccountNumber())) != null;

        String firmBankingUUID = UUID.randomUUID().toString();
        apply(new CheckedRegisteredBankAccountEvent(
                command.getRechargeRequestId(),
                command.getCheckRegisteredBankAccountId(),
                command.getMembershipId(),
                isValid,
                command.getAmount(),
                firmBankingUUID,
                command.getBankName(),
                command.getBankAccountNumber()
        ));
    }

    @EventSourcingHandler
    public void on(CreateRegisteredBankAccountEvent event) {
        log.info("CreatedRegisteredBankAccountEvent Sourcing Handler");

        id = UUID.randomUUID().toString();
        membershipId = event.getMembershipId();
        bankName = event.getBankName();
        bankAccountNumber = event.getBankAccountNumber();
    }

}
