package com.jaypay.membership.application.port.in;

import lombok.Builder;

import java.util.Objects;

@Builder
public record RefreshTokenCommand(String refreshToken) {
    public RefreshTokenCommand {
        Objects.requireNonNull(refreshToken, "refreshToken");
    }
}
