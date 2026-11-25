package com.jaypay.auth.application.port.in;

import com.jaypay.auth.domain.StaffRole;

public record NewStaffAccount(String username, String password, StaffRole role, boolean mfaEnabled) {
}
