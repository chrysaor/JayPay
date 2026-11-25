package com.jaypay.auth.application.port.out;

public interface PasswordHashing {

    String hash(String password);

    boolean matches(String password, String hash);
}
