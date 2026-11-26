package com.jaypay.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsoleGateTest {

    private static final String MEMBER = "/authorize/api/membership/memberships/1";

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        StaffTokens.register(registry);
    }

    @ParameterizedTest(name = "{0} {1}: anonymous {2}, viewer {3}, operator {4}, admin {5}")
    @CsvSource({
            "GET,    /authorize/api/membership/memberships/1,                                                401, 204, 204, 204",
            "GET,    /authorize/api/membership/memberships/address/%EC%84%9C%EC%9A%B8,                        401, 204, 204, 204",
            "PUT,    /authorize/api/membership/memberships/1,                                                401, 403, 204, 204",
            "POST,   /authorize/api/money-aggregation/money/aggregation/get-money-sum-by-address,           401, 204, 204, 204",
            "GET,    /authorize/api/money-query/money/query/get-money-sum-by-address/%EC%84%9C%EC%9A%B8,     401, 204, 204, 204",
            "GET,    /authorize/api/payment/payment/normal-status,                                           401, 204, 204, 204",
            "GET,    /authorize/api/event-flow/flows,                                                        401, 204, 204, 204",
            "GET,    /authorize/api/event-flow/flows?status=FAILED,                                          401, 204, 204, 204",
            "GET,    /authorize/api/event-flow/flows/RECHARGE/0b6f3c1e-9a2d-4c41-8f0e-2f6f1f3b7a10,          401, 204, 204, 204",
            "POST,   /authorize/api/event-flow/flows,                                                        401, 403, 403, 403",
            "DELETE, /authorize/api/event-flow/flows/RECHARGE/1,                                             401, 403, 403, 403",
            "GET,    /authorize/api/event-flow/flows/RECHARGE/1/steps,                                       401, 403, 403, 403",
            "POST,   /authorize/api/payment/payment/finish-settlement,                                       401, 403, 403, 403",
            "POST,   /authorize/api/membership/memberships/register,                                         401, 403, 403, 403",
            "DELETE, /authorize/api/membership/memberships/1,                                                401, 403, 403, 403",
            "GET,    /authorize/api/membership/memberships/1/extra,                                          401, 403, 403, 403",
            "POST,   /authorize/api/payment/payment/request,                                                 401, 403, 403, 403",
            "GET,    /authorize/api/banking/banking/1,                                                       401, 403, 403, 403",
            "GET,    /authorize,                                                                             401, 403, 403, 403",
            "GET,    /staff/me,                                                                              401, 403, 403, 403",
            "POST,   /staff/sign-in,                                                                         401, 403, 403, 403",
            "GET,    /actuator/health,                                                                       401, 403, 403, 403",
            "GET,    /actuator/prometheus,                                                                   401, 403, 403, 403",
            "GET,    /v3/api-docs,                                                                           401, 403, 403, 403",
            "GET,    /swagger-ui/index.html,                                                                 401, 403, 403, 403",
            "GET,    /anything/else,                                                                         401, 403, 403, 403",
    })
    void decidesEveryRequestOfTheAdminConsole(String method, String path, int anonymous, int viewer, int operator, int admin) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), URI.create(path))).andExpect(status().is(anonymous));
        send(StaffTokens.of("viewer"), request(HttpMethod.valueOf(method), URI.create(path))).andExpect(status().is(viewer));
        send(StaffTokens.of("operator"), request(HttpMethod.valueOf(method), URI.create(path))).andExpect(status().is(operator));
        send(StaffTokens.of("admin"), request(HttpMethod.valueOf(method), URI.create(path))).andExpect(status().is(admin));
    }

    @Test
    void aTokenWithSeveralRolesGetsTheHighest() throws Exception {
        send(StaffTokens.of("viewer", "operator", "offline_access"), request(HttpMethod.PUT, MEMBER)).andExpect(status().isNoContent());
    }

    @Test
    void aTokenWithoutAStaffRoleOpensNothing() throws Exception {
        send(StaffTokens.of("default-roles-staff", "offline_access"), get(MEMBER)).andExpect(status().isForbidden());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
        send(StaffTokens.of("admin").signedByAnotherKey(), get(MEMBER)).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        send(StaffTokens.of("admin").expiredFor(Duration.ofMinutes(2)), get(MEMBER)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() throws Exception {
        send(StaffTokens.of("admin").issuedBy("http://admin.jaypay.test/realms/customer"), get(MEMBER)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenForAnotherAudienceIsRefused() throws Exception {
        send(StaffTokens.of("admin").forAudience("account"), get(MEMBER)).andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerTokenIsRefused() throws Exception {
        mockMvc.perform(get(MEMBER).header("Authorization", "Bearer " + StaffTokens.customerToken()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({
            "/authorize/api/membership/memberships/1/..%2F..%2Fregister",
            "/authorize/api/membership/memberships/1/%2E%2E/register",
            "/authorize/api/membership/memberships/1;jsessionid=x",
            "/authorize/api/membership//memberships/1",
            "/authorize/api/membership/memberships/1/../../register",
            "/authorize/api/membership/memberships/%251",
    })
    void refusesAPathThatCouldMeanSomethingElseUpstream(String path) throws Exception {
        send(StaffTokens.of("admin"), get(URI.create(path))).andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousRequestLearnsToSignInAgain() throws Exception {
        mockMvc.perform(get("/authorize/api/payment/payment/normal-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void aForbiddenRequestLearnsItsRoleIsNotEnough() throws Exception {
        send(StaffTokens.of("viewer"), request(HttpMethod.PUT, MEMBER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    private ResultActions send(StaffTokens.Token token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token.value()));
    }
}
