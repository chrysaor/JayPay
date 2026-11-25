package com.jaypay.payment.application.port.in;

import com.jaypay.payment.domain.Payment;

public record SettlementOutcome(Payment payment, boolean alreadySettled) {
}
