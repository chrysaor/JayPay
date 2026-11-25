package com.jaypay.auth.adapter.out.token;

import com.jaypay.auth.application.port.out.StaffTokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
class JwtStaffTokenIssuer implements StaffTokenIssuer {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;

    JwtStaffTokenIssuer(@Value("${staff.token.secret}") String secret,
                        @Value("${staff.token.issuer}") String issuer,
                        @Value("${staff.token.audience}") String audience,
                        @Value("${staff.token.lifetime}") Duration lifetime) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("STAFF_JWT_SECRET needs at least " + MINIMUM_SECRET_BYTES + " bytes.");
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.issuer = issuer;
        this.audience = audience;
        this.lifetime = lifetime;
    }

    @Override
    public IssuedToken issue(String username, Set<String> factors) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(lifetime);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(username)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("factors", List.copyOf(factors))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }
}
