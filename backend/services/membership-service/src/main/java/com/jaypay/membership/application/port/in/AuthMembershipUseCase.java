package com.jaypay.membership.application.port.in;

import com.jaypay.membership.domain.JwtToken;
import com.jaypay.membership.domain.Membership;

public interface AuthMembershipUseCase {

    JwtToken loginMembership(LoginMembershipCommand command);
    JwtToken refreshJwtTokenByRefreshToken(RefreshTokenCommand command);
    boolean validateJwtToken(ValidateTokenCommand command);
    Membership getMembershipByJwtToken(ValidateTokenCommand command);

}
