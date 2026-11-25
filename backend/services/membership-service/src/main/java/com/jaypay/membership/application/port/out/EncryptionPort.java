package com.jaypay.membership.application.port.out;

public interface EncryptionPort {
    String encrypt(String plainText);

    String decrypt(String encryptedText);
}
