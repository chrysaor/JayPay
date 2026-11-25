package com.jaypay.auth.adapter.in.startup;

import com.jaypay.auth.application.port.in.NewStaffAccount;
import com.jaypay.auth.application.port.in.StaffAccountsUseCase;
import com.jaypay.auth.domain.StaffRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class DefaultStaffAccounts implements ApplicationRunner {

    private final StaffAccountsUseCase staffAccounts;
    private final Settings settings;

    @Override
    public void run(ApplicationArguments arguments) {
        for (Settings.Account account : settings.defaultAccounts()) {
            boolean created = staffAccounts.ensureDefaultAccount(
                    new NewStaffAccount(account.username(), account.password(), account.role(), false));
            log.info("{} the default {} account {}", created ? "Created" : "Kept", account.role(), account.username());
        }
    }

    @ConfigurationProperties("staff")
    record Settings(List<Account> defaultAccounts) {

        record Account(StaffRole role, String username, String password) {
        }
    }
}
