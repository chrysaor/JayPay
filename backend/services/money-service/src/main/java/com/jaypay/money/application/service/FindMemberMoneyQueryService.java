package com.jaypay.money.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.application.port.in.FindMemberMoneyListByMembershipIdsQuery;
import com.jaypay.money.application.port.in.FindMemberMoneyQueryUseCase;
import com.jaypay.money.application.port.out.GetMemberMoneyListPort;
import com.jaypay.money.domain.MemberMoney;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.List;


@UseCase
@RequiredArgsConstructor
@Transactional
public class FindMemberMoneyQueryService implements FindMemberMoneyQueryUseCase {

    private final GetMemberMoneyListPort getMemberMoneyListPort;

    @Override
    public List<MemberMoney> findMemberMoneyListByMembershipIds(FindMemberMoneyListByMembershipIdsQuery query) {
        return getMemberMoneyListPort.getMemberMoneys(query.membershipIds());
    }

}
