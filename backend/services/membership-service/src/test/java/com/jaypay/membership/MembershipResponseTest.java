package com.jaypay.membership;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembershipResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void findByIdReturnsOnlyThePublicFields() throws Exception {
        String membershipId = register(uniqueAddress());

        mockMvc.perform(get("/memberships/{membershipId}", membershipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("kim@example.com"))
                .andExpect(onlyPublicFields("$"));
    }

    @Test
    void findByAddressReturnsOnlyThePublicFields() throws Exception {
        String address = uniqueAddress();
        register(address);

        mockMvc.perform(get("/memberships/address/{addressName}", address))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("kim@example.com"))
                .andExpect(onlyPublicFields("$[0]"));
    }

    @Test
    void modifyReturnsOnlyThePublicFields() throws Exception {
        String membershipId = register(uniqueAddress());

        mockMvc.perform(put("/memberships/{membershipId}", membershipId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "%s", "name": "lee", "email": "lee@example.com", "address": "busan", "valid": true, "corp": true}
                                """.formatted(membershipId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("lee"))
                .andExpect(jsonPath("$.email").value("lee@example.com"))
                .andExpect(onlyPublicFields("$"));
    }

    @Test
    void authReturnsOnlyThePublicFields() throws Exception {
        String membershipId = register(uniqueAddress());
        String login = mockMvc.perform(post("/memberships/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "%s"}
                                """.formatted(membershipId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = JsonPath.read(login, "$.refreshToken");

        mockMvc.perform(post("/memberships/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jwtToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(membershipId))
                .andExpect(onlyPublicFields("$"));
    }

    private String register(String address) throws Exception {
        String body = mockMvc.perform(post("/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "kim", "email": "kim@example.com", "address": "%s", "corp": false}
                                """.formatted(address)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.membershipId");
    }

    private static String uniqueAddress() {
        return "address-" + UUID.randomUUID();
    }

    private static ResultMatcher onlyPublicFields(String path) {
        return result -> {
            Map<String, Object> membership = JsonPath.read(result.getResponse().getContentAsString(), path);
            assertThat(membership).containsOnlyKeys("membershipId", "name", "email", "address", "valid", "corp");
        };
    }
}
