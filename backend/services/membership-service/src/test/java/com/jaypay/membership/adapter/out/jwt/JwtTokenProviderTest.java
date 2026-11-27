package com.jaypay.membership.adapter.out.jwt;

import com.jaypay.membership.domain.Membership;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider("a".repeat(64));

    @Test
    void issuedTokensValidateAndCarryTheMembershipId() {
        String jwtToken = provider.generateJwtToken(new Membership.MembershipId("42"));
        String refreshToken = provider.generateRefreshToken(new Membership.MembershipId("42"));

        assertThat(provider.validateJwtToken(jwtToken)).isTrue();
        assertThat(provider.validateJwtToken(refreshToken)).isTrue();
        assertThat(provider.parseMembershipIdFromToken(jwtToken).getMembershipId()).isEqualTo("42");
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(provider.validateJwtToken("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        String foreign = new JwtTokenProvider("b".repeat(64)).generateJwtToken(new Membership.MembershipId("42"));

        assertThat(provider.validateJwtToken(foreign)).isFalse();
    }
}
