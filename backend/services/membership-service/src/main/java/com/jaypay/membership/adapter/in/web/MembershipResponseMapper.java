package com.jaypay.membership.adapter.in.web;

import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.membership.domain.Membership;

final class MembershipResponseMapper {

    private MembershipResponseMapper() {
    }

    static MembershipResponse toResponse(Membership membership) {
        return new MembershipResponse(
                membership.getMembershipId(),
                membership.getName(),
                membership.getEmail(),
                membership.getAddress(),
                membership.isValid(),
                membership.isCorp()
        );
    }
}
