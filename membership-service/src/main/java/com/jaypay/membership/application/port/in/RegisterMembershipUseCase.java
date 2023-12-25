package com.jaypay.membership.application.port.in;

import com.jaypay.membership.domain.Membership;


public interface RegisterMembershipUseCase {

    Membership registerMembership(RegisterMembershipCommand command);

}
