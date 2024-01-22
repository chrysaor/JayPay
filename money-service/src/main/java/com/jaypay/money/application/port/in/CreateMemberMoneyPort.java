package com.jaypay.money.application.port.in;

import com.jaypay.money.domain.MemberMoney;

public interface CreateMemberMoneyPort {

    void createMemberMoney(
            MemberMoney.MembershipId membershipId,
            MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier
    );

}
