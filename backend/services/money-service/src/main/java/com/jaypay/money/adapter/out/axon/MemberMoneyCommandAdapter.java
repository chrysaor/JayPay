package com.jaypay.money.adapter.out.axon;

import com.jaypay.money.adapter.axon.command.MemberMoneyCreatedCommand;
import com.jaypay.money.adapter.axon.command.RechargingMoneyRequestCreateCommand;
import com.jaypay.money.application.port.out.CreateMemberMoneyAggregatePort;
import com.jaypay.money.application.port.out.RegisteredBankAccount;
import com.jaypay.money.application.port.out.RequestMoneyRechargingPort;
import com.jaypay.money.domain.MemberMoney;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberMoneyCommandAdapter implements CreateMemberMoneyAggregatePort, RequestMoneyRechargingPort {

    private final CommandGateway commandGateway;

    @Override
    public CompletableFuture<MemberMoney.MoneyAggregateIdentifier> createMemberMoneyAggregate(MemberMoney.MembershipId membershipId) {
        return commandGateway.send(new MemberMoneyCreatedCommand(membershipId.getMembershipId()))
                .thenApply(result -> new MemberMoney.MoneyAggregateIdentifier(result.toString()));
    }

    @Override
    public void requestMoneyRecharging(MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier,
                                       MemberMoney.MembershipId membershipId,
                                       int amount,
                                       RegisteredBankAccount registeredBankAccount) {
        commandGateway.send(new RechargingMoneyRequestCreateCommand(
                moneyAggregateIdentifier.getAggregateIdentifier(),
                UUID.randomUUID().toString(),
                membershipId.getMembershipId(),
                amount,
                registeredBankAccount.aggregateIdentifier(),
                registeredBankAccount.bankName(),
                registeredBankAccount.bankAccountNumber()
        )).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.warn("{}", throwable.getMessage());
            } else {
                log.info("result: {}", result);
            }
        });
    }

}
