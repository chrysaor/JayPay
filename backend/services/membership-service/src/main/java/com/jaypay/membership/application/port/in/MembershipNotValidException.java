package com.jaypay.membership.application.port.in;

public class MembershipNotValidException extends RuntimeException {

    public MembershipNotValidException(String membershipId) {
        super("The membership " + membershipId + " is not valid, so it cannot sign in.");
    }
}
