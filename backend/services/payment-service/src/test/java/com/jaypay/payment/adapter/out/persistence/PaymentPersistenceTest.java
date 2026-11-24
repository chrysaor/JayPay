package com.jaypay.payment.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PaymentPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataPaymentRepository paymentRepository;

    @Test
    void findsPaymentsByStatus() {
        Date approvedAt = new Date(1_700_000_000_456L);
        paymentRepository.saveAndFlush(new PaymentJpaEntity("1", 10000, "franchise-1", "3.5", 0, approvedAt));
        paymentRepository.saveAndFlush(new PaymentJpaEntity("2", 20000, "franchise-2", "3.5", 2, approvedAt));
        entityManager.clear();

        assertThat(paymentRepository.findByPaymentStatus(0))
                .singleElement()
                .satisfies(payment -> {
                    assertThat(payment)
                            .extracting(
                                    PaymentJpaEntity::getRequestMembershipId,
                                    PaymentJpaEntity::getRequestPrice,
                                    PaymentJpaEntity::getFranchiseId,
                                    PaymentJpaEntity::getFranchiseFeeRate,
                                    PaymentJpaEntity::getPaymentStatus)
                            .containsExactly("1", 10000, "franchise-1", "3.5", 0);
                    assertThat(payment.getApprovedAt().getTime()).isEqualTo(approvedAt.getTime());
                });
    }
}
