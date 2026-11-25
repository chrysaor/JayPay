package com.jaypay.auth.application.port.out;

import java.time.Instant;
import java.util.Set;

public interface StaffTokenIssuer {

    IssuedToken issue(String username, Set<String> factors);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
