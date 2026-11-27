package com.jaypay.money.aggregation.adapter.in.web;

import com.jaypay.money.aggregation.domain.MoneySumNotConfirmedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MoneySumErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Error notConfirmed(MoneySumNotConfirmedException notConfirmed) {
        return new Error("MONEY_SUM_NOT_CONFIRMED", "A service the sum is read from did not answer. Send the same request again.");
    }
}
