package com.jaypay.auth.application.port.in;

import com.jaypay.auth.domain.StaffRole;

public record AccountChange(String actor, String username, StaffRole role, boolean active, boolean mfaEnabled) {
}
