package com.jaypay.banking.application.port.in;

import lombok.Builder;

@Builder
public record RequestBankingInfoCommand(String bankName, String bankAccountNumber) {
}
