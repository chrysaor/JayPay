package com.jaypay.membership.application.port.out;

import com.jaypay.membership.domain.Membership;

public interface AuthMembershipPort {
    // create jwt token by using membership id
    String generateJwtToken(
            Membership.MembershipId membershipId
    );

    // create refresh token by using membership id
    String generateRefreshToken(
            Membership.MembershipId membershipId
    );

    // check whether jwtToken is not expired or not
    boolean validateJwtToken(String jwtToken);

    // check the membership id from jwtToken
    Membership.MembershipId parseMembershipIdFromToken(String jwtToken);
}
