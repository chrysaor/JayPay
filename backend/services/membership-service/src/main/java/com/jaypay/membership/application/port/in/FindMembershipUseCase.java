package com.jaypay.membership.application.port.in;

import com.jaypay.membership.domain.Membership;

import java.util.List;
import java.util.Optional;

public interface FindMembershipUseCase {

    Optional<Membership> findMembership(FindMembershipCommand command);
    boolean isValidMembership(FindMembershipCommand command);
    List<Membership> findMembershipListByAddress(FindMembershipListByAddressCommand command);

}