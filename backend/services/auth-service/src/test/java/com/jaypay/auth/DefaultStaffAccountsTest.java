package com.jaypay.auth;

import com.jaypay.auth.application.port.in.NewStaffAccount;
import com.jaypay.auth.application.port.in.StaffAccountsUseCase;
import com.jaypay.auth.domain.StaffAccountException;
import com.jaypay.auth.domain.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.jaypay.auth.StaffApi.ADMIN;
import static com.jaypay.auth.StaffApi.ADMIN_PASSWORD;
import static com.jaypay.auth.StaffApi.OPERATOR;
import static com.jaypay.auth.StaffApi.STRONG_PASSWORD;
import static com.jaypay.auth.StaffApi.VIEWER;
import static com.jaypay.auth.StaffApi.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DefaultStaffAccountsTest {

    @Autowired
    private StaffAccountsUseCase staffAccounts;

    @Autowired
    private MockMvc mockMvc;

    private StaffApi api;

    @BeforeEach
    void api() {
        api = new StaffApi(mockMvc);
    }

    @Test
    void startupCreatesOneAccountForEveryRole() {
        assertThat(staffAccounts.find(ADMIN)).hasValueSatisfying(account -> assertThat(account.role()).isEqualTo(StaffRole.ADMIN));
        assertThat(staffAccounts.find(OPERATOR)).hasValueSatisfying(account -> assertThat(account.role()).isEqualTo(StaffRole.OPERATOR));
        assertThat(staffAccounts.find(VIEWER)).hasValueSatisfying(account -> assertThat(account.role()).isEqualTo(StaffRole.VIEWER));
    }

    @Test
    void anAccountThatExistsIsLeftAsItIs() throws Exception {
        assertThat(staffAccounts.ensureDefaultAccount(new NewStaffAccount(ADMIN, STRONG_PASSWORD, StaffRole.VIEWER, true))).isFalse();

        api.signIn(ADMIN, STRONG_PASSWORD).andExpect(status().isUnauthorized());
        api.signIn(ADMIN, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.role").value("ADMIN"))
                .andExpect(jsonPath("$.account.mfaEnabled").value(false));
    }

    @Test
    void anAccountThatIsMissingIsCreated() throws Exception {
        String username = uniqueUsername();

        assertThat(staffAccounts.ensureDefaultAccount(new NewStaffAccount(username, STRONG_PASSWORD, StaffRole.OPERATOR, false))).isTrue();

        api.signIn(username, STRONG_PASSWORD).andExpect(jsonPath("$.account.role").value("OPERATOR"));
    }

    @Test
    void aDefaultAccountPassesThePasswordRulesToo() {
        String username = uniqueUsername();

        assertThatThrownBy(() -> staffAccounts.ensureDefaultAccount(new NewStaffAccount(username, "changeme", StaffRole.ADMIN, false)))
                .isInstanceOfSatisfying(StaffAccountException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(StaffAccountException.Reason.WEAK_PASSWORD));
        assertThat(staffAccounts.find(username)).isEmpty();
    }
}
