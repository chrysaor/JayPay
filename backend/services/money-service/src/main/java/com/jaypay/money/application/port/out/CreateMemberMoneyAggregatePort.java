package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;


public interface CreateMemberMoneyAggregatePort {

    MemberMoney.MoneyAggregateIdentifier createMemberMoneyAggregate(MemberMoney.MembershipId membershipId);

}
