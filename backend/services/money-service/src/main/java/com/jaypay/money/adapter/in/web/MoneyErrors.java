package com.jaypay.money.adapter.in.web;

import com.jaypay.money.domain.MembershipNotConfirmedException;
import com.jaypay.money.domain.RegisteredAccountNotConfirmedException;
import com.jaypay.money.domain.RequestIdReusedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MoneyErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error rejected(MoneyRequestRejected rejected) {
        return new Error(rejected.error(), rejected.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error unreadable(HttpMessageNotReadableException unreadable) {
        return new Error("INVALID_REQUEST", "Send a JSON body with requestId, targetMembershipId and amount.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    Error reused(RequestIdReusedException reused) {
        return new Error("REQUEST_ID_REUSED", "This requestId was already used for a different change.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Error notConfirmed(MembershipNotConfirmedException notConfirmed) {
        return new Error("MEMBERSHIP_NOT_CONFIRMED", "The member could not be checked, so nothing changed. Send the same request again.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Error accountNotConfirmed(RegisteredAccountNotConfirmedException notConfirmed) {
        return new Error("REGISTERED_ACCOUNT_NOT_CONFIRMED", "The registered bank account could not be checked, so nothing changed. Send the same request again.");
    }
}
