package com.jaypay.remittance.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RemittancePersistenceTest {

    @Autowired
    private SpringDataRemittanceRepository remittanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimsARequestIdOnce() {
        assertThat(remittanceRepository.claim(UUID.randomUUID(), "once", 1, "MEMBER", 2L, null, null, 1000)).isEqualTo(1);
        assertThat(remittanceRepository.claim(UUID.randomUUID(), "once", 1, "MEMBER", 2L, null, null, 1000)).isZero();
    }

    @Test
    void refusesAnAmountThatIsNotPositive() {
        assertThatThrownBy(() -> remittanceRepository.claim(UUID.randomUUID(), "zero", 1, "MEMBER", 2L, null, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesARemittanceToTheSender() {
        assertThatThrownBy(() -> remittanceRepository.claim(UUID.randomUUID(), "self", 1, "MEMBER", 1L, null, null, 1000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesABankRemittanceToAMember() {
        assertThatThrownBy(() -> remittanceRepository.claim(UUID.randomUUID(), "bank-member", 1, "BANK_ACCOUNT", 2L, null, null, 1000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAMemberRemittanceToABankAccount() {
        assertThatThrownBy(() -> remittanceRepository.claim(UUID.randomUUID(), "member-bank", 1, "MEMBER", null, "Jay Bank", "1002", 1000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void movesOnlyFromTheStatusItExpects() {
        UUID remittanceId = UUID.randomUUID();
        remittanceRepository.claim(remittanceId, "advance", 1, "BANK_ACCOUNT", null, "Jay Bank", "1002", 1000);

        assertThat(remittanceRepository.advance(remittanceId, "DEBITED", "TRANSFERRING")).isZero();
        assertThat(remittanceRepository.advance(remittanceId, "REQUESTED", "DEBITED")).isEqualTo(1);
        assertThat(remittanceRepository.advance(remittanceId, "REQUESTED", "FAILED")).isZero();
        assertThat(remittanceRepository.advance(remittanceId, "DEBITED", "TRANSFERRING")).isEqualTo(1);
        assertThat(remittanceRepository.advance(remittanceId, "DEBITED", "TRANSFERRING")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT remittance_status FROM remittance WHERE remittance_id = ?", String.class, remittanceId))
                .isEqualTo("TRANSFERRING");
    }
}
