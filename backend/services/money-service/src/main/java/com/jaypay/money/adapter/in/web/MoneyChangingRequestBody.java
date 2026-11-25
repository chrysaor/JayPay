package com.jaypay.money.adapter.in.web;

import java.math.BigDecimal;

public record MoneyChangingRequestBody(String requestId, String targetMembershipId, BigDecimal amount) {
}
