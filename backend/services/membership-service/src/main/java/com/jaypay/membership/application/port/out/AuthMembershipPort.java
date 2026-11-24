package com.jaypay.membership.application.port.out;

import com.jaypay.membership.domain.Membership;

public interface AuthMembershipPort {
    String generateJwtToken(
            Membership.MembershipId membershipId
    );

    String generateRefreshToken(
            Membership.MembershipId membershipId
    );

    boolean validateJwtToken(String jwtToken);

    Membership.MembershipId parseMembershipIdFromToken(String jwtToken);
}
