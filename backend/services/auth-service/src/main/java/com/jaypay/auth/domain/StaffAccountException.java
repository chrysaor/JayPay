package com.jaypay.auth.domain;

import java.util.List;

public class StaffAccountException extends RuntimeException {

    public enum Reason {
        INVALID_USERNAME("Use 3 to 64 lowercase letters, digits, dots, hyphens or underscores, starting with a letter or digit."),
        WEAK_PASSWORD("The password breaks the password rules."),
        WRONG_PASSWORD("The current password is wrong."),
        USERNAME_TAKEN("Another staff account already has that username."),
        NOT_FOUND("No staff account has that username."),
        OWN_ACCESS("You cannot change your own role or deactivate yourself."),
        FORBIDDEN("Your role does not allow this.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;
    private final List<PasswordRule> violations;

    public StaffAccountException(Reason reason) {
        this(reason, List.of());
    }

    public StaffAccountException(Reason reason, List<PasswordRule> violations) {
        super(reason.message);
        this.reason = reason;
        this.violations = List.copyOf(violations);
    }

    public Reason reason() {
        return reason;
    }

    public List<PasswordRule> violations() {
        return violations;
    }
}
