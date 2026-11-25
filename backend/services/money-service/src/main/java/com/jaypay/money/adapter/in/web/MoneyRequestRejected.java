package com.jaypay.money.adapter.in.web;

class MoneyRequestRejected extends RuntimeException {

    private final String error;

    MoneyRequestRejected(String error, String message) {
        super(message);
        this.error = error;
    }

    String error() {
        return error;
    }
}
