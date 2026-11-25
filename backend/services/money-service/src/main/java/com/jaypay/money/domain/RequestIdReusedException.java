package com.jaypay.money.domain;

public class RequestIdReusedException extends RuntimeException {

    public RequestIdReusedException(RequestId requestId) {
        super("Request id " + requestId.value() + " was already used for a different change");
    }
}
