package com.jaypay.money.adapter.in.web;

import com.jaypay.money.domain.MoneyChangingRequest;

public record MoneyChangingResultDetail(
        String moneyChangingRequestId,
        int moneyChangingType,
        int moneyChangingResultStatus,
        long amount
) {

    static MoneyChangingResultDetail of(MoneyChangingRequest request) {
        return new MoneyChangingResultDetail(
                request.requestId().value(),
                request.type().code(),
                request.status().code(),
                request.amount().value()
        );
    }
}
