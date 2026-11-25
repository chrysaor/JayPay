package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyAmount;
import com.jaypay.money.domain.RequestId;

public interface RequestMoneyRechargingPort {

    void requestMoneyRecharging(
            RequestId requestId,
            MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier,
            MemberMoney.MembershipId membershipId,
            MoneyAmount amount,
            RegisteredBankAccount registeredBankAccount
    );

}
