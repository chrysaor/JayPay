package com.jaypay.membership.adapter.out.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AESProviderTest {

    private static final String PLAIN_TEXT = "member@example.com";

    private final AESProvider provider = new AESProvider("0123456789abcdef0123456789abcdef");

    @ParameterizedTest
    @ValueSource(ints = {16, 24, 32})
    void decryptsWhatItEncryptedForEveryAesKeyLength(int keyLength) throws Exception {
        AESProvider provider = new AESProvider("k".repeat(keyLength));

        assertThat(provider.decrypt(provider.encrypt(PLAIN_TEXT))).isEqualTo(PLAIN_TEXT);
    }

    @Test
    void decryptsNonAsciiText() throws Exception {
        String plainText = "회원@example.com";

        assertThat(provider.decrypt(provider.encrypt(plainText))).isEqualTo(plainText);
    }

    @Test
    void encryptingTheSamePlainTextTwiceYieldsDifferentCipherTexts() throws Exception {
        String first = provider.encrypt(PLAIN_TEXT);
        String second = provider.encrypt(PLAIN_TEXT);

        assertThat(first).isNotEqualTo(second);
        assertThat(provider.decrypt(first)).isEqualTo(PLAIN_TEXT);
        assertThat(provider.decrypt(second)).isEqualTo(PLAIN_TEXT);
    }

    @Test
    void rejectsACipherTextWithAnyByteChanged() throws Exception {
        byte[] cipherText = Base64.getDecoder().decode(provider.encrypt(PLAIN_TEXT));

        for (int i = 0; i < cipherText.length; i++) {
            byte[] tampered = cipherText.clone();
            tampered[i] ^= 0x01;
            String encoded = Base64.getEncoder().encodeToString(tampered);

            assertThatThrownBy(() -> provider.decrypt(encoded)).isInstanceOf(GeneralSecurityException.class);
        }
    }

    @Test
    void rejectsACipherTextFromAnotherKey() throws Exception {
        String cipherText = new AESProvider("fedcba9876543210fedcba9876543210").encrypt(PLAIN_TEXT);

        assertThatThrownBy(() -> provider.decrypt(cipherText)).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void rejectsACipherTextTooShortToHoldANonceAndATag() {
        String cipherText = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> provider.decrypt(cipherText)).isInstanceOf(GeneralSecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15, 17, 31, 33, 64})
    void refusesAKeyThatIsNotAnAesKeyLength(int keyLength) {
        assertThatThrownBy(() -> new AESProvider("k".repeat(keyLength)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16, 24 or 32 bytes");
    }
}
