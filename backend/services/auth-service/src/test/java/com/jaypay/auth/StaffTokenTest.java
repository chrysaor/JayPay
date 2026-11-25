package com.jaypay.auth;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.jaypay.auth.StaffApi.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffTokenTest {

    private static final String ISSUER = "jaypay-auth-service";
    private static final String AUDIENCE = "jaypay-admin-console";

    @Autowired
    private MockMvc mockMvc;

    @Value("${staff.token.secret}")
    private String secret;

    @Test
    void aSignedInTokenNamesTheAccountTheConsoleAndThePasswordFactor() throws Exception {
        String token = new StaffApi(mockMvc).adminToken();

        Map<String, Object> claims = JsonPath.read(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8), "$");

        assertThat(claims).containsEntry("sub", ADMIN).containsEntry("iss", ISSUER).containsEntry("factors", List.of("FACTOR_PASSWORD"));
        assertThat(claims.get("aud")).isIn(AUDIENCE, List.of(AUDIENCE));
        assertThat(((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue())
                .isEqualTo(Duration.ofHours(8).toSeconds());
    }

    @Test
    void aTokenSignedForTheConsoleOpensIt() throws Exception {
        assertThat(answerTo(staffToken(secret, JWSAlgorithm.HS256, AUDIENCE, Instant.now().plusSeconds(60)))).isEqualTo(200);
    }

    @Test
    void aCustomerTokenFromTheMembershipServiceOpensNothing() throws Exception {
        String customerSecret = "a".repeat(64) + "customer-secret-of-the-membership-service";
        JWTClaimsSet customer = new JWTClaimsSet.Builder()
                .subject("1")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();

        assertThat(answerTo(signed(customerSecret, JWSAlgorithm.HS512, customer))).isEqualTo(401);
    }

    @Test
    void aTokenSignedWithAnotherKeyOpensNothing() throws Exception {
        assertThat(answerTo(staffToken("b".repeat(64), JWSAlgorithm.HS256, AUDIENCE, Instant.now().plusSeconds(60)))).isEqualTo(401);
    }

    @Test
    void aTokenForAnotherAudienceOpensNothing() throws Exception {
        assertThat(answerTo(staffToken(secret, JWSAlgorithm.HS256, "jaypay-customer-site", Instant.now().plusSeconds(60)))).isEqualTo(401);
    }

    @Test
    void anExpiredTokenOpensNothing() throws Exception {
        assertThat(answerTo(staffToken(secret, JWSAlgorithm.HS256, AUDIENCE, Instant.now().minusSeconds(120)))).isEqualTo(401);
    }

    @Test
    void anUnsignedTokenOpensNothing() throws Exception {
        String[] parts = staffToken(secret, JWSAlgorithm.HS256, AUDIENCE, Instant.now().plusSeconds(60)).split("\\.");
        String none = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(answerTo(none + "." + parts[1] + ".")).isEqualTo(401);
    }

    private int answerTo(String token) throws Exception {
        return mockMvc.perform(StaffApi.bearer(get("/staff/me"), token))
                .andReturn().getResponse().getStatus();
    }

    private static String staffToken(String key, JWSAlgorithm algorithm, String audience, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(audience)
                .subject(ADMIN)
                .issueTime(Date.from(expiresAt.minusSeconds(3600)))
                .expirationTime(Date.from(expiresAt))
                .claim("factors", List.of("FACTOR_PASSWORD"))
                .build();
        return signed(key, algorithm, claims);
    }

    private static String signed(String key, JWSAlgorithm algorithm, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(key.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
