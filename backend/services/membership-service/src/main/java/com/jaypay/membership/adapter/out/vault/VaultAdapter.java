package com.jaypay.membership.adapter.out.vault;

import com.jaypay.membership.application.port.out.EncryptionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;

import java.security.GeneralSecurityException;

@Component
@Slf4j
public class VaultAdapter implements EncryptionPort {
    private final AESProvider encryptionProvider;

    public VaultAdapter(VaultTemplate vaultTemplate, @Value("${encryption.fallback-key}") String fallbackKey) {
        String key = fallbackKey;
        try {
            VaultKeyValueOperations ops = vaultTemplate.opsForKeyValue("kv-v1/data/encrypt", VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
            key = (String) ops.get("dbkey").getData().get("key");
        } catch (Exception e) {
            log.warn("Vault key unavailable, using the fallback key: {}", e.getMessage());
        }
        encryptionProvider = new AESProvider(key);
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
