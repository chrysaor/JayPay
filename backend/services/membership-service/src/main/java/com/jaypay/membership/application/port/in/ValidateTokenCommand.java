package com.jaypay.membership.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class ValidateTokenCommand extends SelfValidating<ValidateTokenCommand> {

    @NotNull
    private final String jwtToken;

    public ValidateTokenCommand(String jwtToken) {
        this.jwtToken = jwtToken;
        this.validateSelf();
    }

}
