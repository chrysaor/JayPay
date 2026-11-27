package com.jaypay.banking.adapter.in.axon;

import com.jaypay.banking.adapter.axon.command.CreateRegisteredBankAccountCommand;
import com.jaypay.banking.adapter.axon.event.CreateRegisteredBankAccountEvent;
import com.jaypay.banking.application.port.in.RequestBankingInfoCommand;
import com.jaypay.banking.application.port.in.RequestBankingInfoUseCase;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import com.jaypay.common.event.CheckedRegisteredBankAccountEvent;
import com.jaypay.common.flow.FlowEventPublisher;
import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
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
        log.debug("Registering a bank account for member {}", command.membershipId());
        apply(new CreateRegisteredBankAccountEvent(
                command.membershipId(), command.bankName(), command.bankAccountNumber()
        ));
    }

    @CommandHandler
    public void handle(CheckRegisteredBankAccountCommand command, RequestBankingInfoUseCase requestBankingInfoUseCase,
                       FlowEventPublisher flowEvents) {
        log.debug("Checking the bank account of recharge {}", command.getRechargeRequestId());
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
        flowEvents.publish(command.getRechargeRequestId(), FlowStep.BANK_ACCOUNT_CHECKED,
                isValid ? FlowOutcome.SUCCEEDED : FlowOutcome.FAILED,
                isValid ? null : "The bank did not confirm the account",
                command.getMembershipId(), command.getAmount());
    }

    @EventSourcingHandler
    public void on(CreateRegisteredBankAccountEvent event) {
        id = UUID.randomUUID().toString();
        membershipId = event.membershipId();
        bankName = event.bankName();
        bankAccountNumber = event.bankAccountNumber();
    }

}
