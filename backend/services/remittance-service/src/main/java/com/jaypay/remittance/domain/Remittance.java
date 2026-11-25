package com.jaypay.remittance.domain;

import java.util.UUID;

public record Remittance(UUID remittanceId, NewRemittance request, RemittanceStatus status) {

    public boolean sameRequestAs(NewRemittance other) {
        return request.equals(other);
    }

    public Remittance in(RemittanceStatus next) {
        return new Remittance(remittanceId, request, next);
    }

    public String debitRequestId() {
        return moneyRequestId("decrease");
    }

    public String creditRequestId() {
        return moneyRequestId("increase");
    }

    public String refundRequestId() {
        return moneyRequestId("refund");
    }

    public String transferRequestId() {
        return moneyRequestId("transfer");
    }

    private String moneyRequestId(String change) {
        return "remittance-" + remittanceId + "-" + change;
    }
}
