package com.jaypay.money.application.port.in;

import com.jaypay.money.domain.MemberMoney;

import java.util.List;

public interface FindMemberMoneyQueryUseCase {

    List<MemberMoney> findMemberMoneyListByMembershipIds(FindMemberMoneyListByMembershipIdsQuery query);

}
