package com.jaypay.payment.adapter.in.web;

class PaymentRequestRejected extends RuntimeException {

    private final String error;

    PaymentRequestRejected(String error, String message) {
        super(message);
        this.error = error;
    }

    String error() {
        return error;
    }
}
