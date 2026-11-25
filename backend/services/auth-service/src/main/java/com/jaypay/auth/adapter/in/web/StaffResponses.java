package com.jaypay.auth.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jaypay.auth.application.port.in.SignedIn;
import com.jaypay.auth.domain.PasswordRule;
import com.jaypay.auth.domain.StaffAccount;
import com.jaypay.auth.domain.StaffRole;

import java.util.List;

final class StaffResponses {

    private StaffResponses() {
    }

    record Account(String username, StaffRole role, boolean active, boolean mfaEnabled) {
        static Account of(StaffAccount account) {
            return new Account(account.username(), account.role(), account.active(), account.mfaEnabled());
        }
    }

    record SignIn(String status, String token, String expiresAt, Account account) {
        static SignIn of(SignedIn signedIn) {
            return new SignIn("SIGNED_IN", signedIn.token(), signedIn.expiresAt().toString(), Account.of(signedIn.account()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record Error(String error, String message, List<Violation> violations) {
        Error(String error, String message) {
            this(error, message, List.of());
        }
    }

    record Violation(String rule, String message) {
        static Violation of(PasswordRule rule) {
            return new Violation(rule.name(), rule.message());
        }
    }
}
