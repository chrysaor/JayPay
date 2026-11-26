package com.jaypay.payment.adapter.in.web;

import java.math.BigDecimal;

public record PaymentRequest(String requestId, String requestMembershipId, String franchiseId, BigDecimal requestPrice) {
}
