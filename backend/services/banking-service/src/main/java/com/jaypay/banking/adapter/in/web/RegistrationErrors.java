package com.jaypay.banking.adapter.in.web;

import com.jaypay.banking.domain.MembershipNotConfirmedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegisterBankAccountController.class)
class RegistrationErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Error notConfirmed(MembershipNotConfirmedException notConfirmed) {
        return new Error("MEMBERSHIP_NOT_CONFIRMED", "The member could not be checked, so nothing was registered. Send the same request again.");
    }
}
