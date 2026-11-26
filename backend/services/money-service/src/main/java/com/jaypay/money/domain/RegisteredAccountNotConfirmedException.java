package com.jaypay.money.domain;

public class RegisteredAccountNotConfirmedException extends RuntimeException {

    public RegisteredAccountNotConfirmedException(String membershipId, String answer) {
        super("banking-service did not confirm the registered account of member " + membershipId + ": " + answer);
    }
}
