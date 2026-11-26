package com.jaypay.membership.adapter.out.vault;

import com.jaypay.membership.application.port.out.EncryptionPort;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.security.GeneralSecurityException;

@Component
public class VaultAdapter implements EncryptionPort {
    static final String MOUNT = "kv-v1/data/encrypt";
    static final String SECRET = "dbkey";

    private final AESProvider encryptionProvider;

    public VaultAdapter(VaultTemplate vaultTemplate) {
        encryptionProvider = new AESProvider(readKey(vaultTemplate));
    }

    private static String readKey(VaultTemplate vaultTemplate) {
        VaultResponse response;
        try {
            response = vaultTemplate.opsForKeyValue(MOUNT, VaultKeyValueOperationsSupport.KeyValueBackend.KV_2).get(SECRET);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot read the encryption key from Vault at " + MOUNT + "/" + SECRET, e);
        }
        if (response == null || response.getData() == null || !(response.getData().get("key") instanceof String key)) {
            throw new IllegalStateException("Vault has no field key in " + MOUNT + "/" + SECRET);
        }
        return key;
    }

    @Override
    public String encrypt(String plainText) {
        try {
            return encryptionProvider.encrypt(plainText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        try {
            return encryptionProvider.decrypt(encryptedText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}
