package com.jaypay.membership.adapter.out.jwt;

import com.jaypay.membership.application.port.out.AuthMembershipPort;
import com.jaypay.membership.domain.Membership;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider implements AuthMembershipPort {

    private final SecretKey signKey;
    private final long jwtTokenExpirationInMs;
    private final long refreshTokenExpirationInMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String jwtSecret) {
        this.signKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtTokenExpirationInMs = 1000L * 20;
        this.refreshTokenExpirationInMs = 1000L * 60;
    }

    @Override
    public String generateJwtToken(Membership.MembershipId membershipId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtTokenExpirationInMs);

        return Jwts.builder()
                .subject(membershipId.getMembershipId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signKey, Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public String generateRefreshToken(Membership.MembershipId membershipId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationInMs);

        return Jwts.builder()
                .subject(membershipId.getMembershipId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signKey, Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public boolean validateJwtToken(String jwtToken) {
        try {
            Jwts.parser().verifyWith(signKey).build().parseSignedClaims(jwtToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public Membership.MembershipId parseMembershipIdFromToken(String jwtToken) {
        Claims claims = Jwts.parser().verifyWith(signKey).build().parseSignedClaims(jwtToken).getPayload();
        return new Membership.MembershipId(claims.getSubject());
    }
}
