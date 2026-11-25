package com.jaypay.banking.application.port.in;

import lombok.Builder;

@Builder
public record GetRegisteredBankAccountCommand(String membershipId) {
}
