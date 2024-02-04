package com.jaypay.money.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;
import com.jaypay.money.adapter.out.persistence.MemberMoneyMapper;
import com.jaypay.money.application.port.in.FindMemberMoneyListByMembershipIdsQuery;
import com.jaypay.money.application.port.in.FindMemberMoneyQueryUseCase;
import com.jaypay.money.application.port.out.GetMemberMoneyListPort;
import com.jaypay.money.domain.MemberMoney;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;


@UseCase
@RequiredArgsConstructor
@Transactional
public class FindMemberMoneyQueryService implements FindMemberMoneyQueryUseCase {

    private final MemberMoneyMapper memberMoneyMapper;
    private final GetMemberMoneyListPort getMemberMoneyListPort;

    @Override
    public List<MemberMoney> findMemberMoneyListByMembershipIds(FindMemberMoneyListByMembershipIdsQuery query) {
        // MemberMoney List from membership id list
        List<MemberMoneyJpaEntity> memberMoneyJpaEntities = getMemberMoneyListPort.getMemberMoneys(
                query.getMembershipIds()
        );

        List<MemberMoney> memberMoneyList = new ArrayList<>();
        for (MemberMoneyJpaEntity memberMoneyJpaEntity : memberMoneyJpaEntities) {
            memberMoneyList.add(memberMoneyMapper.mapToDomainEntity(memberMoneyJpaEntity));
        }

        return memberMoneyList;
    }
    
}
