package com.jaypay.banking.application.port.in;

import com.jaypay.common.SelfValidating;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class RequestBankingInfoCommand extends SelfValidating<RequestBankingInfoCommand> {

    @NotNull
    @NotBlank
    private final String bankName;
    @NotNull
    @NotBlank
    private final String bankAccountNumber;

    public RequestBankingInfoCommand(String bankName, String bankAccountNumber) {
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;

        validateSelf();
    }

}