package com.jaypay.money.adapter.out.persistence;

import com.jaypay.money.domain.MemberMoney;
import org.springframework.stereotype.Component;

@Component
public class MemberMoneyMapper {
    public MemberMoney mapToDomainEntity(MemberMoneyJpaEntity memberMoneyJpaEntity) {
        return MemberMoney.generateMemberMoney(
                new MemberMoney.MemberMoneyId(memberMoneyJpaEntity.getMemberMoneyId()+""),
                new MemberMoney.MembershipId(memberMoneyJpaEntity.getMembershipId()+""),
                new MemberMoney.Balance(memberMoneyJpaEntity.getBalance())
        );
    }
}
