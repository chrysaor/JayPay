package com.jaypay.membership.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.membership.application.port.in.RegisterMembershipCommand;
import com.jaypay.membership.application.port.in.RegisterMembershipUseCase;
import com.jaypay.membership.application.port.out.EncryptionPort;
import com.jaypay.membership.application.port.out.RegisterMembershipPort;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;

@UseCase
@RequiredArgsConstructor
@Transactional
public class RegisterMembershipService implements RegisterMembershipUseCase {

    private final RegisterMembershipPort registerMembershipPort;
    private final EncryptionPort encryptionPort;

    @Override
    public Membership registerMembership(RegisterMembershipCommand command) {
        Membership.MembershipName name = new Membership.MembershipName(command.name());
        Membership.MembershipAddress address = new Membership.MembershipAddress(command.address());
        Membership.MembershipIsValid isValid = new Membership.MembershipIsValid(command.isValid());
        Membership.MembershipIsCorp isCorp = new Membership.MembershipIsCorp(command.isCorp());

        Membership.MembershipId membershipId = registerMembershipPort.createMembership(
                name, new Membership.MembershipEmail(encryptionPort.encrypt(command.email())), address, isValid, isCorp
        );

        return Membership.generateMember(
                membershipId, name, new Membership.MembershipEmail(command.email()), address, isValid, isCorp,
                new Membership.MembershipRefreshToken("")
        );
    }

}
