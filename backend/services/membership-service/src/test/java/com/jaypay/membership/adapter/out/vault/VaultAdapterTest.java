package com.jaypay.membership.adapter.out.vault;

import org.junit.jupiter.api.Test;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;

import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaultAdapterTest {

    private static final String KEY = "k".repeat(32);

    @Test
    void encryptsWithTheKeyFromVault() throws Exception {
        VaultAdapter adapter = new VaultAdapter(vaultAnswering(Map.of("key", KEY)));

        String cipherText = adapter.encrypt("member@example.com");

        assertThat(new AESProvider(KEY).decrypt(cipherText)).isEqualTo("member@example.com");
        assertThat(adapter.decrypt(cipherText)).isEqualTo("member@example.com");
    }

    @Test
    void refusesToStartWhenVaultDoesNotAnswer() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        VaultEndpoint endpoint = VaultEndpoint.create("127.0.0.1", closedPort);
        endpoint.setScheme("http");
        VaultTemplate unreachable = new VaultTemplate(endpoint, () -> VaultToken.of("token"));

        assertThatThrownBy(() -> new VaultAdapter(unreachable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kv-v1/data/encrypt")
                .hasRootCauseInstanceOf(ConnectException.class);
    }

    @Test
    void refusesToStartWhenVaultHasNoKey() {
        assertThatThrownBy(() -> new VaultAdapter(vaultAnswering(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dbkey");
    }

    @Test
    void refusesToStartWhenTheSecretHasNoKeyField() {
        assertThatThrownBy(() -> new VaultAdapter(vaultAnswering(Map.of("other", KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dbkey");
    }

    @Test
    void refusesToStartWhenTheTokenIsDenied() {
        VaultTemplate vault = mock(VaultTemplate.class);
        VaultKeyValueOperations operations = mock(VaultKeyValueOperations.class);
        when(vault.opsForKeyValue(VaultAdapter.MOUNT, KeyValueBackend.KV_2)).thenReturn(operations);
        when(operations.get(VaultAdapter.SECRET)).thenThrow(new VaultException("Status 403 Forbidden: permission denied"));

        assertThatThrownBy(() -> new VaultAdapter(vault))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(VaultException.class);
    }

    static VaultTemplate vaultAnswering(Map<String, Object> data) {
        VaultTemplate vault = mock(VaultTemplate.class);
        VaultKeyValueOperations operations = mock(VaultKeyValueOperations.class);
        when(vault.opsForKeyValue(VaultAdapter.MOUNT, KeyValueBackend.KV_2)).thenReturn(operations);
        VaultResponse response = null;
        if (data != null) {
            response = new VaultResponse();
            response.setData(data);
        }
        when(operations.get(VaultAdapter.SECRET)).thenReturn(response);
        return vault;
    }
}
