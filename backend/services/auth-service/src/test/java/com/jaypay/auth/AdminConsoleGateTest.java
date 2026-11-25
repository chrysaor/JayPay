package com.jaypay.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.Map;

import static com.jaypay.auth.StaffApi.ADMIN;
import static com.jaypay.auth.StaffApi.ADMIN_PASSWORD;
import static com.jaypay.auth.StaffApi.OPERATOR;
import static com.jaypay.auth.StaffApi.OPERATOR_PASSWORD;
import static com.jaypay.auth.StaffApi.VIEWER;
import static com.jaypay.auth.StaffApi.VIEWER_PASSWORD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminConsoleGateTest {

    @Autowired
    private MockMvc mockMvc;

    private Map<String, String> tokens;

    @BeforeAll
    void signIn() throws Exception {
        StaffApi api = new StaffApi(mockMvc);
        tokens = Map.of(
                "VIEWER", api.tokenOf(VIEWER, VIEWER_PASSWORD),
                "OPERATOR", api.tokenOf(OPERATOR, OPERATOR_PASSWORD),
                "ADMIN", api.tokenOf(ADMIN, ADMIN_PASSWORD));
    }

    @ParameterizedTest(name = "{0} {1}: anonymous {2}, viewer {3}, operator {4}, admin {5}")
    @CsvSource({
            "GET,    /authorize/api/membership/memberships/1,                                                401, 204, 204, 204",
            "GET,    /authorize/api/membership/memberships/address/%EC%84%9C%EC%9A%B8,                        401, 204, 204, 204",
            "PUT,    /authorize/api/membership/memberships/1,                                                401, 403, 204, 204",
            "POST,   /authorize/api/money-aggregation/money/aggregation/get-money-sum-by-address,           401, 204, 204, 204",
            "GET,    /authorize/api/money-query/money/query/get-money-sum-by-address/%EC%84%9C%EC%9A%B8,     401, 204, 204, 204",
            "GET,    /authorize/api/payment/payment/normal-status,                                           401, 204, 204, 204",
            "POST,   /authorize/api/payment/payment/finish-settlement,                                       401, 403, 204, 204",
            "POST,   /authorize/api/membership/memberships/register,                                         401, 403, 403, 403",
            "DELETE, /authorize/api/membership/memberships/1,                                                401, 403, 403, 403",
            "GET,    /authorize/api/membership/memberships/1/extra,                                          401, 403, 403, 403",
            "POST,   /authorize/api/payment/payment/request,                                                 401, 403, 403, 403",
            "GET,    /authorize/api/banking/banking/1,                                                       401, 403, 403, 403",
            "GET,    /authorize,                                                                             401, 403, 403, 403",
            "GET,    /staff/me,                                                                              401, 200, 200, 200",
            "GET,    /staff/accounts,                                                                        401, 403, 403, 200",
            "GET,    /actuator/health,                                                                       200, 200, 200, 200",
            "GET,    /actuator/prometheus,                                                                   200, 200, 200, 200",
            "GET,    /v3/api-docs,                                                                           200, 200, 200, 200",
            "POST,   /actuator/health,                                                                       401, 403, 403, 403",
            "GET,    /anything/else,                                                                         401, 403, 403, 403",
    })
    void decidesEveryRequestOfTheAdminConsole(String method, String path, int anonymous, int viewer, int operator, int admin) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), URI.create(path))).andExpect(status().is(anonymous));
        mockMvc.perform(as("VIEWER", request(HttpMethod.valueOf(method), URI.create(path)))).andExpect(status().is(viewer));
        mockMvc.perform(as("OPERATOR", request(HttpMethod.valueOf(method), URI.create(path)))).andExpect(status().is(operator));
        mockMvc.perform(as("ADMIN", request(HttpMethod.valueOf(method), URI.create(path)))).andExpect(status().is(admin));
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
        mockMvc.perform(as("ADMIN", get(URI.create(path)))).andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousRequestLearnsToSignInAgain() throws Exception {
        mockMvc.perform(get("/authorize/api/payment/payment/normal-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void aForbiddenRequestLearnsItsRoleIsNotEnough() throws Exception {
        mockMvc.perform(as("VIEWER", request(HttpMethod.POST, "/authorize/api/payment/payment/finish-settlement")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    private MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return StaffApi.bearer(request, tokens.get(role));
    }
}
