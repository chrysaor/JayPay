package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;

public interface GetMemberMoneyPort {

    MemberMoney.MoneyAggregateIdentifier getMoneyAggregateIdentifier(MemberMoney.MembershipId membershipId);

}
