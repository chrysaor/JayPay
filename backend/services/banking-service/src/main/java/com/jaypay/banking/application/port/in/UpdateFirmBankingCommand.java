package com.jaypay.banking.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class UpdateFirmBankingCommand extends SelfValidating<UpdateFirmBankingCommand> {

    @NotNull
    private final String firmBankingAggregateIdentifier;

    @NotNull
    private final int firmBankingStatus;

    public UpdateFirmBankingCommand(String firmBankingAggregateIdentifier, int firmBankingStatus) {
        this.firmBankingAggregateIdentifier = firmBankingAggregateIdentifier;
        this.firmBankingStatus = firmBankingStatus;

        this.validateSelf();
    }

}
