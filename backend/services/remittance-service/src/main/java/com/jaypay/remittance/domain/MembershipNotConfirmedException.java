package com.jaypay.remittance.domain;

public class MembershipNotConfirmedException extends RuntimeException {

    public MembershipNotConfirmedException(String membershipId, String answer) {
        super("membership-service did not confirm member " + membershipId + ": " + answer);
    }
}
