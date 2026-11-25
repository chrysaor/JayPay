package com.jaypay.banking.adapter.in.web;

import com.jaypay.banking.domain.RequestIdReusedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RequestFirmBankingController.class)
class TransferErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error rejected(TransferRequestRejected rejected) {
        return new Error(rejected.error(), rejected.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error unreadable(HttpMessageNotReadableException unreadable) {
        return new Error("INVALID_REQUEST", "Send a JSON body with requestId, both bank accounts and moneyAmount.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    Error reused(RequestIdReusedException reused) {
        return new Error("REQUEST_ID_REUSED", "This requestId was already used for a different transfer.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Error notConfirmed(TransferNotConfirmedException notConfirmed) {
        return new Error("TRANSFER_NOT_CONFIRMED", "The bank did not confirm the transfer. Send the same request again.");
    }
}
