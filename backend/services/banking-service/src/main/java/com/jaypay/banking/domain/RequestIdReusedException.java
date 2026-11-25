package com.jaypay.banking.domain;

public class RequestIdReusedException extends RuntimeException {

    public RequestIdReusedException(String requestId) {
        super("The requestId " + requestId + " was already used for a different transfer.");
    }
}
