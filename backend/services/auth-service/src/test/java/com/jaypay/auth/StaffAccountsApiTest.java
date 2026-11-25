package com.jaypay.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static com.jaypay.auth.StaffApi.ADMIN;
import static com.jaypay.auth.StaffApi.ADMIN_PASSWORD;
import static com.jaypay.auth.StaffApi.OPERATOR;
import static com.jaypay.auth.StaffApi.OPERATOR_PASSWORD;
import static com.jaypay.auth.StaffApi.STRONG_PASSWORD;
import static com.jaypay.auth.StaffApi.VIEWER;
import static com.jaypay.auth.StaffApi.VIEWER_PASSWORD;
import static com.jaypay.auth.StaffApi.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAccountsApiTest {

    @Autowired
    private MockMvc mockMvc;

    private StaffApi api;

    @BeforeEach
    void api() {
        api = new StaffApi(mockMvc);
    }

    @Test
    void signInAnswersWithATokenThatExpiresAndTheAccountWithoutItsPassword() throws Exception {
        String body = api.signIn(ADMIN, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED_IN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.account.username").value(ADMIN))
                .andExpect(jsonPath("$.account.role").value("ADMIN"))
                .andExpect(jsonPath("$.account.active").value(true))
                .andExpect(jsonPath("$.account.mfaEnabled").value(false))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> account = JsonPath.read(body, "$.account");
        assertThat(account).containsOnlyKeys("username", "role", "active", "mfaEnabled");
        assertThat(Instant.parse(JsonPath.read(body, "$.expiresAt"))).isAfter(Instant.now());
    }

    @Test
    void aWrongPasswordAndAnUnknownUsernameGetTheSameAnswer() throws Exception {
        api.signIn(ADMIN, "Wrong-Password-000")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        api.signIn("nobody-here", ADMIN_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        api.signIn(ADMIN, ADMIN_PASSWORD + "x".repeat(80))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meAnswersWithTheSignedInAccountAndNeedsAToken() throws Exception {
        api.me(api.tokenOf(OPERATOR, OPERATOR_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(OPERATOR))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
        mockMvc.perform(get("/staff/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void anAdminCreatesAnAccountThatCanSignIn() throws Exception {
        String username = uniqueUsername();

        api.createAccount(api.adminToken(), username, STRONG_PASSWORD, "VIEWER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mfaEnabled").value(false));

        api.signIn(username, STRONG_PASSWORD).andExpect(status().isOk());
        api.accounts(api.adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem(username)));
    }

    @Test
    void aWeakPasswordIsRejectedWithEveryRuleItBreaks() throws Exception {
        String username = uniqueUsername();

        api.createAccount(api.adminToken(), username, "harbor-" + username, "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.violations[*].rule").value(contains("UPPERCASE", "DIGIT", "NOT_USERNAME")))
                .andExpect(jsonPath("$.violations[0].message").value("Include an uppercase letter."));

        api.signIn(username, "harbor-" + username).andExpect(status().isUnauthorized());
    }

    @Test
    void anInvalidOrTakenUsernameIsRejected() throws Exception {
        String admin = api.adminToken();

        api.createAccount(admin, "Not Valid", STRONG_PASSWORD, "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_USERNAME"));
        api.createAccount(admin, OPERATOR, STRONG_PASSWORD, "VIEWER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
        api.signIn(OPERATOR, OPERATOR_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void onlyAnAdminManagesAccounts() throws Exception {
        for (String token : new String[]{api.tokenOf(OPERATOR, OPERATOR_PASSWORD), api.tokenOf(VIEWER, VIEWER_PASSWORD)}) {
            api.accounts(token)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
            api.createAccount(token, uniqueUsername(), STRONG_PASSWORD, "ADMIN").andExpect(status().isForbidden());
            api.changeAccount(token, VIEWER, "ADMIN", true, false).andExpect(status().isForbidden());
        }
        api.me(api.tokenOf(VIEWER, VIEWER_PASSWORD)).andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void anAdminChangesTheRoleAndTheMfaFlagOfAnotherAccount() throws Exception {
        String username = api.createdAccount("VIEWER");

        api.changeAccount(api.adminToken(), username, "OPERATOR", true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.mfaEnabled").value(true));

        api.me(api.tokenOf(username, STRONG_PASSWORD))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.mfaEnabled").value(true));
    }

    @Test
    void aRoleChangeReachesTokensIssuedBeforeIt() throws Exception {
        String username = api.createdAccount("VIEWER");
        String token = api.tokenOf(username, STRONG_PASSWORD);
        api.accounts(token).andExpect(status().isForbidden());

        api.changeAccount(api.adminToken(), username, "ADMIN", true, false).andExpect(status().isOk());

        api.accounts(token).andExpect(status().isOk());
    }

    @Test
    void aDeactivatedAccountCannotSignInAndItsTokenStopsWorking() throws Exception {
        String username = api.createdAccount("OPERATOR");
        String token = api.tokenOf(username, STRONG_PASSWORD);

        api.changeAccount(api.adminToken(), username, "OPERATOR", false, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        api.signIn(username, STRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        api.me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCannotDemoteOrDeactivateThemselves() throws Exception {
        String admin = api.adminToken();

        api.changeAccount(admin, ADMIN, "OPERATOR", true, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OWN_ACCESS"));
        api.changeAccount(admin, ADMIN, "ADMIN", false, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OWN_ACCESS"));

        api.me(admin).andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void changingAnUnknownAccountIsNotFound() throws Exception {
        api.changeAccount(api.adminToken(), uniqueUsername(), "VIEWER", true, false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void aMissingRoleIsABadRequest() throws Exception {
        api.createAccount(api.adminToken(), uniqueUsername(), STRONG_PASSWORD, "OWNER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void staffChangeTheirOwnPasswordUnderTheSameRules() throws Exception {
        String username = api.createdAccount("VIEWER");
        String token = api.tokenOf(username, STRONG_PASSWORD);
        String next = "Granite-Owl-Meadow-63";

        api.changePassword(token, "Wrong-Current-Pass-1", next)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));
        api.changePassword(token, STRONG_PASSWORD, "password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.violations[*].rule", hasItem("MIN_LENGTH")));
        api.changePassword(token, STRONG_PASSWORD, next).andExpect(status().isNoContent());

        api.signIn(username, STRONG_PASSWORD).andExpect(status().isUnauthorized());
        api.signIn(username, next).andExpect(status().isOk());
    }
}
