package com.jaypay.money.adapter.out.axon;

import com.jaypay.money.adapter.axon.command.MemberMoneyCreatedCommand;
import com.jaypay.money.adapter.axon.command.RechargingMoneyRequestCreateCommand;
import com.jaypay.money.application.port.out.CreateMemberMoneyAggregatePort;
import com.jaypay.money.application.port.out.RegisteredBankAccount;
import com.jaypay.money.application.port.out.RequestMoneyRechargingPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyAmount;
import com.jaypay.money.domain.RequestId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

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
    public void requestMoneyRecharging(RequestId requestId,
                                       MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier,
                                       MemberMoney.MembershipId membershipId,
                                       MoneyAmount amount,
                                       RegisteredBankAccount registeredBankAccount) {
        commandGateway.send(new RechargingMoneyRequestCreateCommand(
                moneyAggregateIdentifier.getAggregateIdentifier(),
                requestId.value(),
                membershipId.getMembershipId(),
                Math.toIntExact(amount.value()),
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
