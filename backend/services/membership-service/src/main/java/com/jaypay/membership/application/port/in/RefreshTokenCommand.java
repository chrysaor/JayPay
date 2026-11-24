package com.jaypay.membership.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public
class RefreshTokenCommand extends SelfValidating<RefreshTokenCommand> {
    @NotNull
    private final String refreshToken;

    public RefreshTokenCommand(String refreshToken) {
        this.refreshToken = refreshToken;
        this.validateSelf();
    }

}
