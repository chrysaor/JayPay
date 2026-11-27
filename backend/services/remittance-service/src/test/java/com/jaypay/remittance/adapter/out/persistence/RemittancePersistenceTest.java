package com.jaypay.remittance.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sweepsAnUnfinishedRemittanceOnceForEachRetryAfterItWaitedUpToItsAttempts() {
        claimed("sweep-fresh");
        UUID due = claimed("sweep-due");
        for (String finished : List.of("COMPLETED", "FAILED", "REFUNDED")) {
            UUID remittanceId = claimed("sweep-" + finished);
            remittanceRepository.advance(remittanceId, "REQUESTED", finished);
            requestedSecondsAgo(remittanceId, 1000);
        }

        requestedSecondsAgo(due, 90);
        assertThat(sweptRequestIds()).containsExactly("sweep-due");
        assertThat(sweptRequestIds()).isEmpty();

        remittanceRepository.advance(due, "REQUESTED", "REFUNDING");
        requestedSecondsAgo(due, 150);
        assertThat(sweptRequestIds()).containsExactly("sweep-due");

        requestedSecondsAgo(due, 1000);
        assertThat(sweptRequestIds()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT sweep_attempts FROM remittance WHERE remittance_id = ?", Integer.class, due))
                .isEqualTo(2);
    }

    private UUID claimed(String requestId) {
        UUID remittanceId = UUID.randomUUID();
        remittanceRepository.claim(remittanceId, requestId, 1, "MEMBER", 2L, null, null, 1000);
        return remittanceId;
    }

    private List<String> sweptRequestIds() {
        return remittanceRepository.sweep(List.of("REQUESTED", "DEBITED", "TRANSFERRING", "REFUNDING"), 60, 2).stream()
                .map(RemittanceJpaEntity::getRequestId)
                .filter(requestId -> requestId.startsWith("sweep-"))
                .toList();
    }

    private void requestedSecondsAgo(UUID remittanceId, int seconds) {
        jdbcTemplate.update("UPDATE remittance SET requested_at = now() - make_interval(secs => ?) WHERE remittance_id = ?", seconds, remittanceId);
    }
}
