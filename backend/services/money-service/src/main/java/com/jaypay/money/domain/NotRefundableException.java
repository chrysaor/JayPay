package com.jaypay.money.domain;

public class NotRefundableException extends RuntimeException {

    public NotRefundableException(RequestId refundedRequestId) {
        super("Request id " + refundedRequestId.value() + " names no decrease that can still be refunded");
    }
}
