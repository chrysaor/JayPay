package com.jaypay.auth.application.port.in;

import com.jaypay.auth.domain.StaffAccount;

import java.time.Instant;

public record SignedIn(String token, Instant expiresAt, StaffAccount account) {
}
