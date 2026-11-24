package com.jaypay.membership.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.membership.application.port.in.ModifyMembershipCommand;
import com.jaypay.membership.application.port.in.ModifyMembershipUseCase;
import com.jaypay.membership.application.port.out.EncryptionPort;
import com.jaypay.membership.application.port.out.ModifyMembershipPort;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;

@UseCase
@RequiredArgsConstructor
@Transactional
public class ModifyMembershipService implements ModifyMembershipUseCase {

    private final ModifyMembershipPort modifyMembershipPort;
    private final EncryptionPort encryptionPort;

    @Override
    public Membership modifyMembership(ModifyMembershipCommand command) {
        Membership.MembershipId membershipId = new Membership.MembershipId(command.membershipId());
        Membership.MembershipName name = new Membership.MembershipName(command.name());
        Membership.MembershipAddress address = new Membership.MembershipAddress(command.address());
        Membership.MembershipIsValid isValid = new Membership.MembershipIsValid(command.isValid());
        Membership.MembershipIsCorp isCorp = new Membership.MembershipIsCorp(command.isCorp());
        Membership.MembershipRefreshToken refreshToken = new Membership.MembershipRefreshToken("");

        modifyMembershipPort.modifyMembership(
                membershipId, name, new Membership.MembershipEmail(encryptionPort.encrypt(command.email())),
                address, isValid, isCorp, refreshToken
        );

        return Membership.generateMember(
                membershipId, name, new Membership.MembershipEmail(command.email()), address, isValid, isCorp, refreshToken
        );
    }
}
