package com.jaypay.auth.application.port.in;

public record PasswordChange(String username, String currentPassword, String newPassword) {
}
