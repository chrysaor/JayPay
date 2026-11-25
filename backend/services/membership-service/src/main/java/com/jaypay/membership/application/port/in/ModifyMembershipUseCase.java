package com.jaypay.membership.application.port.in;

import com.jaypay.membership.domain.Membership;

public interface ModifyMembershipUseCase {
    Membership modifyMembership(ModifyMembershipCommand command);
}
