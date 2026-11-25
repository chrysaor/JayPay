package com.jaypay.banking.application.port.in;

import lombok.Builder;

@Builder
public record RegisterBankAccountCommand(
        String membershipId,
        String bankName,
        String bankAccountNumber,
        boolean isValid
) {
}
