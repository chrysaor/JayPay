package com.jaypay.banking.application.port.in;

import lombok.Builder;

@Builder
public record RequestFirmBankingCommand(
        String fromBankName,
        String fromBankAccountNumber,
        String toBankName,
        String toBankAccountNumber,
        int moneyAmount
) {
}
