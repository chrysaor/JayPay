package com.jaypay.auth.domain;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static com.jaypay.auth.domain.StaffAccountException.Reason.INVALID_USERNAME;
import static com.jaypay.auth.domain.StaffAccountException.Reason.OWN_ACCESS;

public record StaffAccount(String username, String passwordHash, StaffRole role, boolean active, boolean mfaEnabled) {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");

    public static StaffAccount open(String username, String password, StaffRole role, boolean mfaEnabled, UnaryOperator<String> hash) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new StaffAccountException(INVALID_USERNAME);
        }
        PasswordPolicy.check(username, password);
        return new StaffAccount(username, hash.apply(password), role, true, mfaEnabled);
    }

    public StaffAccount withPassword(String password, UnaryOperator<String> hash) {
        PasswordPolicy.check(username, password);
        return new StaffAccount(username, hash.apply(password), role, active, mfaEnabled);
    }

    public StaffAccount changedBy(String actor, StaffRole role, boolean active, boolean mfaEnabled) {
        if (username.equals(actor) && (role != this.role || active != this.active)) {
            throw new StaffAccountException(OWN_ACCESS);
        }
        return new StaffAccount(username, passwordHash, role, active, mfaEnabled);
    }
}
