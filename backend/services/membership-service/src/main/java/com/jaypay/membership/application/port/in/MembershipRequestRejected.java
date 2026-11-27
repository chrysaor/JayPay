package com.jaypay.membership.application.port.in;

public class MembershipRequestRejected extends RuntimeException {

    private final String error;

    public MembershipRequestRejected(String error, String message) {
        super(message);
        this.error = error;
    }

    public String error() {
        return error;
    }
}
