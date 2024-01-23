package com.jaypay.money.application.port.in;

import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;
import com.jaypay.money.domain.MemberMoney;

public interface GetMemberMoneyPort {

    MemberMoneyJpaEntity getMemberMoney(
            MemberMoney.MembershipId membershipId
    );

}
