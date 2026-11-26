package com.jaypay.payment.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
}
