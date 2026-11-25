package com.jaypay.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

record StaffApi(MockMvc mockMvc) {

    static final String ADMIN = "test-admin";
    static final String ADMIN_PASSWORD = "Aaaaaaaaaaaa1!";
    static final String OPERATOR = "test-operator";
    static final String OPERATOR_PASSWORD = "Oooooooooooo2!";
    static final String VIEWER = "test-viewer";
    static final String VIEWER_PASSWORD = "Vvvvvvvvvvvv3!";
    static final String STRONG_PASSWORD = "Ssssssssssss4!";

    private static final RandomGenerator RANDOM = new SecureRandom();

    static String uniqueUsername() {
        return "staff-" + RANDOM.ints(10, 'a', 'z' + 1)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
    }

    ResultActions signIn(String username, String password) throws Exception {
        return mockMvc.perform(post("/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    String tokenOf(String username, String password) throws Exception {
        String body = signIn(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    String adminToken() throws Exception {
        return tokenOf(ADMIN, ADMIN_PASSWORD);
    }

    ResultActions createAccount(String token, String username, String password, String role) throws Exception {
        return mockMvc.perform(bearer(post("/staff/accounts"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s", "role": "%s", "mfaEnabled": false}
                        """.formatted(username, password, role)));
    }

    String createdAccount(String role) throws Exception {
        String username = uniqueUsername();
        createAccount(adminToken(), username, STRONG_PASSWORD, role).andExpect(status().isOk());
        return username;
    }

    ResultActions changeAccount(String token, String username, String role, boolean active, boolean mfaEnabled) throws Exception {
        return mockMvc.perform(bearer(put("/staff/accounts/{username}", username), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role": "%s", "active": %s, "mfaEnabled": %s}
                        """.formatted(role, active, mfaEnabled)));
    }

    ResultActions changePassword(String token, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(bearer(put("/staff/me/password"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(currentPassword, newPassword)));
    }

    ResultActions me(String token) throws Exception {
        return mockMvc.perform(bearer(get("/staff/me"), token));
    }

    ResultActions accounts(String token) throws Exception {
        return mockMvc.perform(bearer(get("/staff/accounts"), token));
    }

    static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
