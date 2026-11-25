package com.jaypay.auth.adapter.in.web;

import com.jaypay.auth.domain.StaffRole;

import java.util.Arrays;
import java.util.Objects;

final class StaffRequests {

    private StaffRequests() {
    }

    record SignIn(String username, String password) {
        SignIn {
            required(username, password);
        }
    }

    record ChangePassword(String currentPassword, String newPassword) {
        ChangePassword {
            required(currentPassword, newPassword);
        }
    }

    record CreateAccount(String username, String password, StaffRole role, Boolean mfaEnabled) {
        CreateAccount {
            required(username, password, role, mfaEnabled);
        }
    }

    record ChangeAccount(StaffRole role, Boolean active, Boolean mfaEnabled) {
        ChangeAccount {
            required(role, active, mfaEnabled);
        }
    }

    private static void required(Object... fields) {
        if (Arrays.stream(fields).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Send every field of the request.");
        }
    }
}
