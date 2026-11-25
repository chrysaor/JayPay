package com.jaypay.membership.application.port.in;

import lombok.Builder;

import java.util.Objects;

@Builder
public record ValidateTokenCommand(String jwtToken) {
    public ValidateTokenCommand {
        Objects.requireNonNull(jwtToken, "jwtToken");
    }
}
