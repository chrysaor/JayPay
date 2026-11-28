package com.jaypay.money.query.domain;

public class MemberAddressNotConfirmedException extends RuntimeException {

    public MemberAddressNotConfirmedException(String membershipId, String answer) {
        super("membership-service did not give the address of member " + membershipId + ": " + answer);
    }
}
