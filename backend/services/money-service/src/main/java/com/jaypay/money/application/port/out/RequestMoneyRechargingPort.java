package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;

public interface RequestMoneyRechargingPort {

    void requestMoneyRecharging(
            MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier,
            MemberMoney.MembershipId membershipId,
            int amount,
            RegisteredBankAccount registeredBankAccount
    );

}
