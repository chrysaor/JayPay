package com.jaypay.membership.application.port.out;

import com.jaypay.membership.domain.Membership;

public interface RegisterMembershipPort {
    Membership.MembershipId createMembership(
            Membership.MembershipName membershipName,
            Membership.MembershipEmail membershipEmail,
            Membership.MembershipAddress membershipAddress,
            Membership.MembershipIsValid membershipIsValid,
            Membership.MembershipIsCorp membershipIsCorp
    );
}
