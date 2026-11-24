package com.jaypay.money.adapter.in.axon;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.money.adapter.axon.command.MemberMoneyCreatedCommand;
import com.jaypay.money.adapter.axon.command.RechargingMoneyRequestCreateCommand;
import com.jaypay.money.adapter.axon.event.IncreaseMemberMoneyEvent;
import com.jaypay.money.adapter.axon.event.MemberMoneyCreatedEvent;
import com.jaypay.money.adapter.axon.event.RechargingRequestCreatedEvent;
import lombok.Data;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
@Data
@Slf4j
public class MemberMoneyAggregate {

    @AggregateIdentifier
    private String id;
    private Long membershipId;
    private int balance;

    @CommandHandler
    public MemberMoneyAggregate(MemberMoneyCreatedCommand command) {
        log.info("MemberMoneyCreatedCommand Handler");

        apply(new MemberMoneyCreatedEvent(command.membershipId()));
    }

    @CommandHandler
    public void handler(RechargingMoneyRequestCreateCommand command) {
        log.info("RechargingMoneyRequestCreateCommand Handler");
        id = command.aggregateIdentifier();

        apply(new RechargingRequestCreatedEvent(
                command.rechargingRequestId(),
                command.membershipId(),
                command.amount(),
                command.registeredBankAccountAggregateIdentifier(),
                command.bankName(),
                command.bankAccountNumber()
        ));
    }

    @EventSourcingHandler
    public void on(MemberMoneyCreatedEvent event) {
        log.info("MemberMoneyCreatedEvent Sourcing Handler");
        id = UUID.randomUUID().toString();
        membershipId = Long.parseLong(event.membershipId());
        balance = 0;
    }

    @EventSourcingHandler
    public void on(IncreaseMemberMoneyEvent event) {
        log.info("IncreaseMemberMoneyEvent Sourcing Handler");
        id = event.aggregateIdentifier();
        membershipId = Long.parseLong(event.targetMembershipId());
        balance = event.amount();
    }

    public MemberMoneyAggregate() {
    }
}
