package com.jaypay.money.adapter.axon.aggregate;

import com.jaypay.money.adapter.axon.command.IncreaseMemberMoneyCommand;
import com.jaypay.money.adapter.axon.command.MemberMoneyCreatedCommand;
import com.jaypay.money.adapter.axon.command.RechargingMoneyRequestCreateCommand;
import com.jaypay.money.adapter.axon.event.IncreaseMemberMoneyEvent;
import com.jaypay.money.adapter.axon.event.MemberMoneyCreatedEvent;
import com.jaypay.money.adapter.axon.event.RechargingRequestCreatedEvent;
import com.jaypay.money.application.port.in.GetRegisteredBankAccountPort;
import com.jaypay.money.application.port.in.RegisteredBankAccountAggregateIdentifier;
import lombok.Data;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import javax.validation.constraints.NotNull;
import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
@Data
public class MemberMoneyAggregate {

    @AggregateIdentifier
    private String id;
    private Long membershipId;
    private int balance;

    @CommandHandler
    public MemberMoneyAggregate(MemberMoneyCreatedCommand command) {
        System.out.println("MemberMoneyCreatedCommand Handler");

        apply(new MemberMoneyCreatedEvent(command.getMembershipId()));
    }

    @CommandHandler
    public String handler(@NotNull IncreaseMemberMoneyCommand command) {
        System.out.println("IncreaseMemberMoneyCommand Handler");
        id = command.getAggregateIdentifier();

        // store event
        apply(new IncreaseMemberMoneyEvent(id, command.getMembershipId(), command.getAmount()));
        return id;
    }

    @CommandHandler
    public void handler(RechargingMoneyRequestCreateCommand command, GetRegisteredBankAccountPort getRegisteredBankAccountPort) {
        System.out.println("RechargingMoneyRequestCreateCommand Handler");
        id = command.getAggregateIdentifier();

        // Saga Start - new RechargingRequestCreatedEvent
        // It needs banking information -> Bank svc (get RegisteredBankAccount)
        RegisteredBankAccountAggregateIdentifier aggregateIdentifier = getRegisteredBankAccountPort.getRegisteredBankAccount(
                command.getMembershipId()
        );

        apply(new RechargingRequestCreatedEvent(
                command.getRechargingRequestId(),
                command.getMembershipId(),
                command.getAmount(),
                aggregateIdentifier.getAggregateIdentifier(),
                aggregateIdentifier.getBankName(),
                aggregateIdentifier.getBankAccountNumber()
        ));

    }

    @EventSourcingHandler
    public void on(MemberMoneyCreatedEvent event) {
        System.out.println("MemberMoneyCreatedEvent Sourcing Handler");
        id = UUID.randomUUID().toString();
        membershipId = Long.parseLong(event.getMembershipId());
        balance = 0;
    }

    @EventSourcingHandler
    public void on(IncreaseMemberMoneyEvent event) {
        System.out.println("IncreaseMemberMoneyEvent Sourcing Handler");
        id = event.getAggregateIdentifier();
        membershipId = Long.parseLong(event.getTargetMembershipId());
        balance = event.getAmount();
    }

    public MemberMoneyAggregate() {
    }
}
