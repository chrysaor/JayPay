package com.jaypay.membership.adapter.in.web;

import com.jaypay.membership.application.port.in.MembershipNotFoundException;
import com.jaypay.membership.application.port.in.MembershipNotValidException;
import com.jaypay.membership.application.port.in.TokenRefusedException;
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

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Error notValid(MembershipNotValidException notValid) {
        return new Error("MEMBERSHIP_NOT_VALID", notValid.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Error refused(TokenRefusedException refused) {
        return new Error("TOKEN_REFUSED", refused.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Error notFound(MembershipNotFoundException notFound) {
        return new Error("MEMBERSHIP_NOT_FOUND", notFound.getMessage());
    }
}
