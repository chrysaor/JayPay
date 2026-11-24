package com.jaypay.membership.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.adapter.out.persistence.MembershipMapper;
import com.jaypay.membership.application.port.in.ModifyMembershipCommand;
import com.jaypay.membership.application.port.in.ModifyMembershipUseCase;
import com.jaypay.membership.application.port.out.ModifyMembershipPort;
import com.jaypay.membership.domain.Membership;

import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;

@UseCase
@RequiredArgsConstructor
@Transactional
public class ModifyMembershipService implements ModifyMembershipUseCase {

    private final ModifyMembershipPort modifyMembershipPort;
    private final MembershipMapper membershipMapper;

    @Override
    public Membership modifyMembership(ModifyMembershipCommand command) {
        MembershipJpaEntity jpaEntity = modifyMembershipPort.modifyMembership(
                new Membership.MembershipId(command.getMembershipId()),
                new Membership.MembershipName(command.getName()),
                new Membership.MembershipEmail(command.getEmail()),
                new Membership.MembershipAddress(command.getAddress()),
                new Membership.MembershipIsValid(command.isValid()),
                new Membership.MembershipIsCorp(command.isCorp()),
                new Membership.MembershipRefreshToken("")
        );

        return membershipMapper.mapToDomainEntity(jpaEntity);
    }
}
