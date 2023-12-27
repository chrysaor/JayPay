package com.jaypay.membership.application.service;


import com.jaypay.common.UseCase;
import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.adapter.out.persistence.MembershipMapper;
import com.jaypay.membership.application.port.in.FindMembershipCommand;
import com.jaypay.membership.application.port.in.FindMembershipUseCase;
import com.jaypay.membership.application.port.out.FindMembershipPort;
import com.jaypay.membership.domain.Membership;

import lombok.RequiredArgsConstructor;


import javax.transaction.Transactional;

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
}
