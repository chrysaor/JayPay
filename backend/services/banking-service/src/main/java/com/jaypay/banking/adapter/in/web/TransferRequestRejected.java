package com.jaypay.banking.adapter.in.web;

class TransferRequestRejected extends RuntimeException {

    private final String error;

    TransferRequestRejected(String error, String message) {
        super(message);
        this.error = error;
    }

    String error() {
        return error;
    }
}
