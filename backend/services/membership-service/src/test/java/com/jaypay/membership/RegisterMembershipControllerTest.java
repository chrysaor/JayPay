package com.jaypay.membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersMembershipAndReturnsItWithPlainEmail() throws Exception {
        mockMvc.perform(post("/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "name", "email": "email", "address": "address", "corp": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").isNotEmpty())
                .andExpect(jsonPath("$.name").value("name"))
                .andExpect(jsonPath("$.email").value("email"))
                .andExpect(jsonPath("$.address").value("address"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.corp").value(false))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void refusesABlankAddressWith400() throws Exception {
        mockMvc.perform(post("/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "name", "email": "email", "address": " ", "corp": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ADDRESS_REQUIRED"));
    }
}
