package com.jaypay.membership.application.service;


import com.jaypay.common.UseCase;
import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.adapter.out.persistence.MembershipMapper;
import com.jaypay.membership.application.port.in.FindMembershipCommand;
import com.jaypay.membership.application.port.in.FindMembershipListByAddressCommand;
import com.jaypay.membership.application.port.in.FindMembershipUseCase;
import com.jaypay.membership.application.port.out.FindMembershipPort;
import com.jaypay.membership.domain.Membership;

import lombok.RequiredArgsConstructor;


import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@UseCase
@Transactional
public class FindMembershipService implements FindMembershipUseCase {

    private final FindMembershipPort findMembershipPort;
    private final MembershipMapper membershipMapper;

    @Override
    public Membership findMembership(FindMembershipCommand command) {
        MembershipJpaEntity entity = findMembershipPort.findMembership(
                new Membership.MembershipId(command.getMembershipId())
        );
        return membershipMapper.mapToDomainEntity(entity);
    }

    @Override
    public List<Membership> findMembershipListByAddress(FindMembershipListByAddressCommand command) {
        List<MembershipJpaEntity> membershipJpaEntities = findMembershipPort.findMembershipListByAddress(
                new Membership.MembershipAddress(command.getAddressName())
        );

        List<Membership> memberships = new ArrayList<>();
        for (MembershipJpaEntity membershipJpaEntity : membershipJpaEntities) {
            memberships.add(membershipMapper.mapToDomainEntity(membershipJpaEntity));
        }

        return memberships;
    }
}
