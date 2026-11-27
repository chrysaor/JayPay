package com.jaypay.membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModifyMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void refusesToMakeAMembershipInvalidWith400() throws Exception {
        mockMvc.perform(put("/memberships/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "1", "name": "name", "email": "email", "address": "address", "valid": false, "corp": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MEMBERSHIP_MUST_STAY_VALID"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void refusesABlankAddressWith400() throws Exception {
        mockMvc.perform(put("/memberships/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "1", "name": "name", "email": "email", "address": " ", "valid": true, "corp": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ADDRESS_REQUIRED"));
    }
}
