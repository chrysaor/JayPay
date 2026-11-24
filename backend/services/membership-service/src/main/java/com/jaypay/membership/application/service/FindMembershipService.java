package com.jaypay.membership.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.membership.application.port.in.FindMembershipCommand;
import com.jaypay.membership.application.port.in.FindMembershipListByAddressCommand;
import com.jaypay.membership.application.port.in.FindMembershipUseCase;
import com.jaypay.membership.application.port.out.EncryptionPort;
import com.jaypay.membership.application.port.out.FindMembershipPort;
import com.jaypay.membership.domain.Membership;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.List;

@RequiredArgsConstructor
@UseCase
@Transactional
public class FindMembershipService implements FindMembershipUseCase {

    private final FindMembershipPort findMembershipPort;
    private final EncryptionPort encryptionPort;

    @Override
    public Membership findMembership(FindMembershipCommand command) {
        Membership stored = findMembershipPort.findMembership(new Membership.MembershipId(command.membershipId()));
        return stored.withEmail(encryptionPort.decrypt(stored.getEmail()));
    }

    @Override
    public List<Membership> findMembershipListByAddress(FindMembershipListByAddressCommand command) {
        return findMembershipPort.findMembershipListByAddress(new Membership.MembershipAddress(command.addressName())).stream()
                .map(stored -> stored.withEmail(encryptionPort.decrypt(stored.getEmail())))
                .toList();
    }
}
