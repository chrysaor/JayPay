package com.jaypay.remittance.adapter.in.web;

class RemittanceRequestRejected extends RuntimeException {

    private final String error;

    RemittanceRequestRejected(String error, String message) {
        super(message);
        this.error = error;
    }

    String error() {
        return error;
    }
}
