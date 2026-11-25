package com.jaypay.membership.adapter.out.vault;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AESProvider {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final Set<Integer> KEY_LENGTHS = Set.of(16, 24, 32);
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec secretKey;

    public AESProvider(String key) {
        byte[] keyBytes = key.getBytes(UTF_8);
        if (!KEY_LENGTHS.contains(keyBytes.length)) {
            throw new IllegalArgumentException("AES key must be 16, 24 or 32 bytes, got " + keyBytes.length);
        }
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        byte[] sealed = cipher.doFinal(plainText.getBytes(UTF_8));
        byte[] cipherText = ByteBuffer.allocate(NONCE_LENGTH + sealed.length).put(nonce).put(sealed).array();
        return Base64.getEncoder().encodeToString(cipherText);
    }

    public String decrypt(String encryptedText) throws GeneralSecurityException {
        byte[] cipherText = Base64.getDecoder().decode(encryptedText);
        if (cipherText.length < NONCE_LENGTH + TAG_LENGTH_BITS / Byte.SIZE) {
            throw new AEADBadTagException("Ciphertext is shorter than a nonce and a tag");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, cipherText, 0, NONCE_LENGTH));
        byte[] plainText = cipher.doFinal(cipherText, NONCE_LENGTH, cipherText.length - NONCE_LENGTH);
        return new String(plainText, UTF_8);
    }
}
