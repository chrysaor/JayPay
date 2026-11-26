package com.jaypay.membership.application.port.out;

import com.jaypay.membership.domain.Membership;

import java.util.List;
import java.util.Optional;

public interface FindMembershipPort {
    Optional<Membership> findMembership(Membership.MembershipId membershipId);

    boolean isValidMembership(Membership.MembershipId membershipId);

    List<Membership> findMembershipListByAddress(Membership.MembershipAddress membershipAddress);
}
