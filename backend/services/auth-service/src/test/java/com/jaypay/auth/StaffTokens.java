package com.jaypay.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StaffTokens {

    static final String ISSUER = "http://admin.jaypay.test/realms/staff";
    static final String AUDIENCE = "jaypay-admin-console";

    private static final RSAKey REALM_KEY = rsaKey();
    private static final RSAKey FOREIGN_KEY = rsaKey();
    private static final HttpServer REALM_CERTS = serve(new JWKSet(REALM_KEY.toPublicJWK()));

    private StaffTokens() {
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("STAFF_ISSUER_URI", () -> ISSUER);
        registry.add("STAFF_JWK_SET_URI", () -> "http://127.0.0.1:" + REALM_CERTS.getAddress().getPort() + "/certs");
    }

    static Token of(String... realmRoles) {
        return new Token(REALM_KEY, ISSUER, List.of(AUDIENCE), Duration.ofMinutes(2), List.of(realmRoles));
    }

    record Token(RSAKey key, String issuer, List<String> audience, Duration lifetime, List<String> realmRoles) {

        Token signedByAnotherKey() {
            return new Token(FOREIGN_KEY, issuer, audience, lifetime, realmRoles);
        }

        Token issuedBy(String other) {
            return new Token(key, other, audience, lifetime, realmRoles);
        }

        Token forAudience(String... other) {
            return new Token(key, issuer, List.of(other), lifetime, realmRoles);
        }

        Token expiredFor(Duration ago) {
            return new Token(key, issuer, audience, ago.negated(), realmRoles);
        }

        String value() {
            Instant now = Instant.now();
            Instant issuedAt = lifetime.isNegative() ? now.plus(lifetime).minus(Duration.ofMinutes(2)) : now;
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(UUID.randomUUID().toString())
                    .claim("preferred_username", "staff-" + String.join("-", realmRoles))
                    .claim("realm_access", Map.of("roles", realmRoles))
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(now.plus(lifetime)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            try {
                jwt.sign(new RSASSASigner(key));
            } catch (JOSEException failure) {
                throw new IllegalStateException(failure);
            }
            return jwt.serialize();
        }
    }

    static String customerToken() throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("1")
                .expirationTime(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .build());
        jwt.sign(new MACSigner("customer-token-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static RSAKey rsaKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static HttpServer serve(JWKSet keys) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = keys.toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/certs", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
