package com.jaypay.membership;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aSignInAnswersTokensThatValidate() throws Exception {
        String membershipId = register();

        String login = signIn(membershipId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(membershipId))
                .andReturn().getResponse().getContentAsString();

        for (String field : new String[]{"jwtToken", "refreshToken"}) {
            send("/memberships/token-validate", "jwtToken", JsonPath.read(login, "$." + field))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }
    }

    @Test
    void theRefreshTokenOfTheLastSignInAnswersANewAccessTokenForTheSameMember() throws Exception {
        String membershipId = register();
        String refreshToken = JsonPath.read(signIn(membershipId).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        String refreshed = send("/memberships/refresh-token", "refreshToken", refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipId").value(membershipId))
                .andExpect(jsonPath("$.refreshToken").value(refreshToken))
                .andReturn().getResponse().getContentAsString();

        send("/memberships/token-validate", "jwtToken", JsonPath.read(refreshed, "$.jwtToken"))
                .andExpect(content().string("true"));
    }

    @Test
    void anAccessTokenIsNotARefreshToken() throws Exception {
        String accessToken = JsonPath.read(signIn(register()).andReturn().getResponse().getContentAsString(), "$.jwtToken");

        String answer = send("/memberships/refresh-token", "refreshToken", accessToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(answer).isEmpty();
    }

    @Test
    void aTextThatIsNoTokenRefreshesNothingAndNamesNoMember() throws Exception {
        assertThat(send("/memberships/refresh-token", "refreshToken", "not-a-token")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEmpty();
        assertThat(send("/memberships/auth", "jwtToken", "not-a-token")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void aSignInOfAMemberThatDoesNotExistIsRefusedWith404() throws Exception {
        signIn("999999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MEMBERSHIP_NOT_FOUND"));
    }

    private ResultActions signIn(String membershipId) throws Exception {
        return send("/memberships/login", "membershipId", membershipId);
    }

    private ResultActions send(String path, String field, String value) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"%s": "%s"}
                        """.formatted(field, value)));
    }

    private String register() throws Exception {
        String body = mockMvc.perform(post("/memberships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "kim", "email": "kim@example.com", "address": "address-%s", "corp": false}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.membershipId");
    }
}
