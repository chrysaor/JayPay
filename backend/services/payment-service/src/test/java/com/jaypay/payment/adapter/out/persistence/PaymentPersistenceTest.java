package com.jaypay.payment.adapter.out.persistence;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PaymentPersistenceTest {

    @Autowired
    private SpringDataPaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimsARequestIdOnce() {
        assertThat(paymentRepository.claim("once", 1, 2, 1000, 300, 30, 970)).isEqualTo(1);
        assertThat(paymentRepository.claim("once", 1, 2, 1000, 300, 30, 970)).isZero();
    }

    @Test
    void refusesARowWhoseFeeAndPayoutDoNotMakeThePrice() {
        assertThatThrownBy(() -> paymentRepository.claim("broken", 1, 2, 1000, 300, 30, 1000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAPriceThatIsNotPositive() {
        assertThatThrownBy(() -> paymentRepository.claim("zero", 1, 2, 0, 300, 0, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAFeeRateAboveTheWhole() {
        assertThatThrownBy(() -> paymentRepository.claim("rate", 1, 2, 1000, 10001, 1000, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void settlesOnlyAPaidPaymentAndOnlyOnce() {
        paymentRepository.claim("settle", 1, 2, 1000, 300, 30, 970);
        long paymentId = paymentRepository.findByRequestId("settle").orElseThrow().getPaymentId();

        assertThat(paymentRepository.settle(paymentId)).isZero();
        assertThat(paymentRepository.conclude(paymentId, "PAID")).isEqualTo(1);
        assertThat(paymentRepository.conclude(paymentId, "FAILED")).isZero();
        assertThat(paymentRepository.settle(paymentId)).isEqualTo(1);
        assertThat(paymentRepository.settle(paymentId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT payment_status FROM payment WHERE payment_id = ?", String.class, paymentId))
                .isEqualTo("SETTLED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sweepsARequestedPaymentOnceForEachRetryAfterItWaitedUpToItsAttempts() {
        paymentRepository.claim("sweep-fresh", 1, 2, 1000, 300, 30, 970);
        paymentRepository.claim("sweep-paid", 1, 2, 1000, 300, 30, 970);
        paymentRepository.claim("sweep-due", 1, 2, 1000, 300, 30, 970);
        paymentRepository.conclude(paymentId("sweep-paid"), "PAID");
        requestedSecondsAgo("sweep-paid", 1000);

        requestedSecondsAgo("sweep-due", 90);
        assertThat(sweptRequestIds()).containsExactly("sweep-due");
        assertThat(sweptRequestIds()).isEmpty();

        requestedSecondsAgo("sweep-due", 150);
        assertThat(sweptRequestIds()).containsExactly("sweep-due");

        requestedSecondsAgo("sweep-due", 1000);
        assertThat(sweptRequestIds()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT sweep_attempts FROM payment WHERE request_id = 'sweep-due'", Integer.class))
                .isEqualTo(2);
    }

    private List<String> sweptRequestIds() {
        return paymentRepository.sweep(60, 2).stream()
                .map(PaymentJpaEntity::getRequestId)
                .filter(requestId -> requestId.startsWith("sweep-"))
                .toList();
    }

    private long paymentId(String requestId) {
        return jdbcTemplate.queryForObject("SELECT payment_id FROM payment WHERE request_id = ?", Long.class, requestId);
    }

    private void requestedSecondsAgo(String requestId, int seconds) {
        jdbcTemplate.update("UPDATE payment SET requested_at = now() - make_interval(secs => ?) WHERE request_id = ?", seconds, requestId);
    }
}
