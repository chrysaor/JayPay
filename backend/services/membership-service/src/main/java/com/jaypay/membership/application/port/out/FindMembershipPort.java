package com.jaypay.membership.application.port.out;

import com.jaypay.membership.domain.Membership;

import java.util.List;

public interface FindMembershipPort {
    Membership findMembership(Membership.MembershipId membershipId);

    List<Membership> findMembershipListByAddress(Membership.MembershipAddress membershipAddress);
}
