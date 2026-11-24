package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;

import java.util.concurrent.CompletableFuture;

public interface CreateMemberMoneyAggregatePort {

    CompletableFuture<MemberMoney.MoneyAggregateIdentifier> createMemberMoneyAggregate(MemberMoney.MembershipId membershipId);

}
