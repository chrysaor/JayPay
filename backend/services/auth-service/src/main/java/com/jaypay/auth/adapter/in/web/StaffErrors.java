package com.jaypay.auth.adapter.in.web;

import com.jaypay.auth.domain.StaffAccountException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class StaffErrors {

    @ExceptionHandler
    ResponseEntity<StaffResponses.Error> rejected(StaffAccountException rejected) {
        HttpStatus status = switch (rejected.reason()) {
            case INVALID_USERNAME, WEAK_PASSWORD, WRONG_PASSWORD -> HttpStatus.BAD_REQUEST;
            case USERNAME_TAKEN, OWN_ACCESS -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return ResponseEntity.status(status).body(new StaffResponses.Error(rejected.reason().name(), rejected.getMessage(),
                rejected.violations().stream().map(StaffResponses.Violation::of).toList()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    StaffResponses.Error signInFailed(AuthenticationException failed) {
        return new StaffResponses.Error("INVALID_CREDENTIALS", "The username or password is wrong.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    StaffResponses.Error unreadable(HttpMessageNotReadableException unreadable) {
        return new StaffResponses.Error("INVALID_REQUEST", "Send every field of the request with a known value.");
    }
}
