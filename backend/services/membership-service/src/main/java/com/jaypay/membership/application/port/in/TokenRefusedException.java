package com.jaypay.membership.application.port.in;

public class TokenRefusedException extends RuntimeException {

    public TokenRefusedException(String reason) {
        super(reason);
    }
}
