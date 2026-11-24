package com.jaypay.money.application.port.out;

import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;

import java.util.List;

public interface GetMemberMoneyListPort {

    List<MemberMoneyJpaEntity> getMemberMoneys(List<String> membershipIds);

}
