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
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberMoneyCommandAdapter implements CreateMemberMoneyAggregatePort, RequestMoneyRechargingPort {

    private final CommandGateway commandGateway;

    @Override
    public MemberMoney.MoneyAggregateIdentifier createMemberMoneyAggregate(MemberMoney.MembershipId membershipId) {
        Object aggregateIdentifier = commandGateway.sendAndWait(new MemberMoneyCreatedCommand(membershipId.getMembershipId()));
        return new MemberMoney.MoneyAggregateIdentifier(aggregateIdentifier.toString());
    }

    @Override
    public void requestMoneyRecharging(RequestId requestId,
                                       MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier,
                                       MemberMoney.MembershipId membershipId,
                                       MoneyAmount amount,
                                       RegisteredBankAccount registeredBankAccount) {
        commandGateway.sendAndWait(new RechargingMoneyRequestCreateCommand(
                moneyAggregateIdentifier.getAggregateIdentifier(),
                requestId.value(),
                membershipId.getMembershipId(),
                amount.value(),
                registeredBankAccount.aggregateIdentifier(),
                registeredBankAccount.bankName(),
                registeredBankAccount.bankAccountNumber()));
    }
}
