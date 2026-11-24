package com.jaypay.banking.application.port.in;

import lombok.Builder;

@Builder
public record UpdateFirmBankingCommand(String firmBankingAggregateIdentifier, int firmBankingStatus) {
}
