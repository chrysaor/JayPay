package com.jaypay.membership.application.port.out;

import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.domain.Membership;

public interface FindMembershipPort {
    MembershipJpaEntity findMembership(
            Membership.MembershipId membershipId
    );
}
