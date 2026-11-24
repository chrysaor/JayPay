package com.jaypay.membership.adapter.out.jwt;

import com.jaypay.membership.application.port.out.AuthMembershipPort;
import com.jaypay.membership.domain.Membership;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider implements AuthMembershipPort {

    private final Key signKey;
    private final long jwtTokenExpirationInMs; // expired time of jwt token
    private final long refreshTokenExpirationInMs; // expired time of refresh token

    public JwtTokenProvider() {
        // 512bit 알고리즘 지원을 위한 비밀키 입니다.
        // 512 bit = 64 byte
        // env 등을 통해서, 외부 환경변수로부터 데이터를 받아올 수도 있어요.
        // inner secret key
        String jwtSecret = "NYd4nEtyLtcU7cpS/1HTFVmQJd7MmrP+HafWoXZjWNOL7qKccOOUfQNEx5yvG6dfdpuBeyMs9eEbRmdBrPQCNg==";
        this.signKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtTokenExpirationInMs = 1000L * 20; // 20초
        this.refreshTokenExpirationInMs = 1000L * 60; // 60초
    }

    @Override
    public String generateJwtToken(Membership.MembershipId membershipId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtTokenExpirationInMs);

        return Jwts.builder()
                .setSubject(membershipId.getMembershipId())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signKey, SignatureAlgorithm.HS512)
                .compact();
    }

    @Override
    public String generateRefreshToken(Membership.MembershipId membershipId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationInMs);

        return Jwts.builder()
                .setSubject(membershipId.getMembershipId())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(signKey, SignatureAlgorithm.HS512)
                .compact();
    }

    @Override
    public boolean validateJwtToken(String jwtToken) {
        try {
            Jwts.parserBuilder().setSigningKey(signKey).build().parseClaimsJws(jwtToken);
            return true;
        } catch (MalformedJwtException ex) {
            // Invalid JWT token
        } catch (ExpiredJwtException ex) {
            // Expired JWT token
        } catch (UnsupportedJwtException ex) {
            // Unsupported JWT token
        } catch (IllegalArgumentException ex) {
            // JWT claims string is empty
        }
        return false;
    }

    @Override
    public Membership.MembershipId parseMembershipIdFromToken(String jwtToken) {
        Claims claims = Jwts.parserBuilder().setSigningKey(signKey).build().parseClaimsJws(jwtToken).getBody();
        return new Membership.MembershipId(claims.getSubject());
    }
}
