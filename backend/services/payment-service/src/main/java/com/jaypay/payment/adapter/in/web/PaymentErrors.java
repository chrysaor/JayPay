package com.jaypay.payment.adapter.in.web;

import com.jaypay.payment.domain.PaymentRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class PaymentErrors {

    record Error(String error, String message) {
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error rejected(PaymentRequestRejected rejected) {
        return new Error(rejected.error(), rejected.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error unreadable(HttpMessageNotReadableException unreadable) {
        return new Error("INVALID_REQUEST", "Send a JSON body with the fields this endpoint documents.");
    }

    @ExceptionHandler
    ResponseEntity<Error> refused(PaymentRejectedException refused) {
        return switch (refused.reason()) {
            case INVALID_PAYER -> error(HttpStatus.BAD_REQUEST, refused, "The paying member does not exist or is not valid.");
            case INVALID_MERCHANT -> error(HttpStatus.BAD_REQUEST, refused, "The merchant is not a valid corporate member.");
            case REQUEST_ID_REUSED -> error(HttpStatus.CONFLICT, refused, "This requestId was already used for a different payment.");
            case PAYMENT_NOT_FOUND -> error(HttpStatus.NOT_FOUND, refused, "No payment has this paymentId.");
            case PAYMENT_NOT_PAID -> error(HttpStatus.CONFLICT, refused, "Only a paid payment can be settled.");
            case PAYMENT_NOT_CONFIRMED -> error(HttpStatus.SERVICE_UNAVAILABLE, refused,
                    "The wallet did not confirm the payment. Send the same request again.");
        };
    }

    private static ResponseEntity<Error> error(HttpStatus status, PaymentRejectedException refused, String message) {
        return ResponseEntity.status(status).body(new Error(refused.reason().name(), message));
    }
}
