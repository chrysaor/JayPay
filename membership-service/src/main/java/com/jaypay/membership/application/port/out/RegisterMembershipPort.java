package com.jaypay.membership.application.port.out;

import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.domain.Membership;

public interface RegisterMembershipPort {

    MembershipJpaEntity createMembership(
        Membership.MembershipName membershipName,
        Membership.MembershipEmail membershipEmail,
        Membership.MembershipAddress membershipAddress,
        Membership.MembershipIsValid membershipIsValid,
        Membership.MembershipIsCorp membershipIsCorp
    );
}
