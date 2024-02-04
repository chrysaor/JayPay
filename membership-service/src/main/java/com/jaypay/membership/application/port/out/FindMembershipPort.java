package com.jaypay.membership.application.port.out;

import com.jaypay.membership.adapter.out.persistence.MembershipJpaEntity;
import com.jaypay.membership.domain.Membership;

import java.util.List;

public interface FindMembershipPort {
    MembershipJpaEntity findMembership(
            Membership.MembershipId membershipId
    );

    List<MembershipJpaEntity> findMembershipListByAddress(
            Membership.MembershipAddress membershipAddress
    );
}
