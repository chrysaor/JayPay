package com.jaypay.membership.adapter.in.web;

import com.jaypay.membership.application.port.in.MembershipRequestRejected;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MembershipErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error rejected(MembershipRequestRejected rejected) {
        return new Error(rejected.error(), rejected.getMessage());
    }
}
