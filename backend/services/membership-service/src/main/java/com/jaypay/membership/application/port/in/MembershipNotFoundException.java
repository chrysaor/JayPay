package com.jaypay.membership.application.port.in;

public class MembershipNotFoundException extends RuntimeException {

    public MembershipNotFoundException(String membershipId) {
        super("No membership has the id " + membershipId + ".");
    }
}
