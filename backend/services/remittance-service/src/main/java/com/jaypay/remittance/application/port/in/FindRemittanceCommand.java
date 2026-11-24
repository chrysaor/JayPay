package com.jaypay.remittance.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;


@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class FindRemittanceCommand extends SelfValidating<FindRemittanceCommand> {

    @NotNull
    private String membershipId;

    public FindRemittanceCommand(String membershipId) {
        this.membershipId = membershipId;
        this.validateSelf();
    }

}
