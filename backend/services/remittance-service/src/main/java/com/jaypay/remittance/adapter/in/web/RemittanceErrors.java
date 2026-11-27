package com.jaypay.remittance.adapter.in.web;

import com.jaypay.remittance.domain.MembershipNotConfirmedException;
import com.jaypay.remittance.domain.RemittanceRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RemittanceErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error rejected(RemittanceRequestRejected rejected) {
        return new Error(rejected.error(), rejected.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error unreadable(HttpMessageNotReadableException unreadable) {
        return new Error("INVALID_REQUEST", "Send a JSON body with the fields this endpoint documents.");
    }

    @ExceptionHandler
    ResponseEntity<Error> refused(RemittanceRejectedException refused) {
        return switch (refused.reason()) {
            case INVALID_SENDER -> error(HttpStatus.BAD_REQUEST, refused, "The sending member does not exist or is not valid.");
            case INVALID_RECEIVER -> error(HttpStatus.BAD_REQUEST, refused, "The receiving member does not exist or is not valid.");
            case INVALID_BANK_ACCOUNT -> error(HttpStatus.BAD_REQUEST, refused, "The bank does not know this account.");
            case REQUEST_ID_REUSED -> error(HttpStatus.CONFLICT, refused, "This requestId was already used for a different remittance.");
            case REMITTANCE_NOT_CONFIRMED -> error(HttpStatus.SERVICE_UNAVAILABLE, refused,
                    "The wallet did not confirm the remittance. Send the same request again.");
        };
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Error notConfirmed(MembershipNotConfirmedException notConfirmed) {
        return new Error("MEMBERSHIP_NOT_CONFIRMED", "A member could not be checked, so nothing changed. Send the same request again.");
    }

    private static ResponseEntity<Error> error(HttpStatus status, RemittanceRejectedException refused, String message) {
        return ResponseEntity.status(status).body(new Error(refused.reason().name(), message));
    }
}
