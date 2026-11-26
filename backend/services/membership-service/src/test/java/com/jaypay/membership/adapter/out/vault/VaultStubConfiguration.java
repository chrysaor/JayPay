package com.jaypay.membership.adapter.out.vault;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.vault.core.VaultTemplate;

import java.util.Map;

@Configuration
class VaultStubConfiguration {

    @Bean
    @Primary
    VaultTemplate vaultAnsweringTheTestKey() {
        return VaultAdapterTest.vaultAnswering(Map.of("key", "test-only-key-16"));
    }
}
