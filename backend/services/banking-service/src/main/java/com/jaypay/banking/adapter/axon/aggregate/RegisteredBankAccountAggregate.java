package com.jaypay.banking.adapter.axon.aggregate;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.axon.event.CreateRegisteredBankAccountEvent;
import com.jaypay.banking.adapter.out.external.bank.BankAccount;
import com.jaypay.banking.adapter.out.external.bank.GetBankAccountRequest;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import com.jaypay.common.event.CheckedRegisteredBankAccountEvent;
import lombok.Data;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import jakarta.validation.constraints.NotNull;
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
    public RegisteredBankAccountAggregate(@NotNull CreateRegisteredBankAccountCommand command) {
        log.info("CreateRegisteredBankAccountCommand Sourcing Handler");

        apply(new CreateRegisteredBankAccountEvent(
                command.getMembershipId(), command.getBankName(), command.getBankAccountNumber()
        ));
    }

    @CommandHandler
    public void handle(@NotNull CheckRegisteredBankAccountCommand command, RequestBankAccountInfoPort requestBankAccountInfoPort) {
        log.info("CheckRegisteredBankAccountCommand Handler");

        id = command.getAggregateIdentifier();

        BankAccount bankAccount = requestBankAccountInfoPort.getBankAccountInfo(new GetBankAccountRequest(
                command.getBankName(), command.getBankAccountNumber()
        ));

        String firmBankingUUID = UUID.randomUUID().toString();
        apply(new CheckedRegisteredBankAccountEvent(
                command.getRechargeRequestId(),
                command.getCheckRegisteredBankAccountId(),
                command.getMembershipId(),
                bankAccount.isValid(),
                command.getAmount(),
                firmBankingUUID,
                bankAccount.getBankName(),
                bankAccount.getBankAccountNumber()
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
