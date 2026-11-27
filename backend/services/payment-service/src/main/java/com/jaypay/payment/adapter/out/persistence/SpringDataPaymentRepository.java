package com.jaypay.payment.adapter.out.persistence;

import com.jaypay.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByRequestId(String requestId);

    List<PaymentJpaEntity> findByPaymentStatusOrderByPaymentId(PaymentStatus paymentStatus);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO payment (request_id, request_membership_id, franchise_id, request_price, fee_rate_basis_points, fee, payout,
                                 payment_status, requested_at)
            VALUES (:requestId, :requestMembershipId, :franchiseId, :requestPrice, :feeRateBasisPoints, :fee, :payout, 'REQUESTED', now())
            ON CONFLICT (request_id) DO NOTHING""")
    int claim(@Param("requestId") String requestId, @Param("requestMembershipId") long requestMembershipId,
              @Param("franchiseId") long franchiseId, @Param("requestPrice") long requestPrice,
              @Param("feeRateBasisPoints") int feeRateBasisPoints, @Param("fee") long fee, @Param("payout") long payout);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE payment SET payment_status = :outcome, approved_at = CASE WHEN :outcome = 'PAID' THEN now() END
            WHERE payment_id = :paymentId AND payment_status = 'REQUESTED'""")
    int conclude(@Param("paymentId") long paymentId, @Param("outcome") String outcome);

    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE payment SET sweep_attempts = sweep_attempts + 1
            WHERE payment_id IN (SELECT payment_id FROM payment
                                 WHERE payment_status = 'REQUESTED' AND sweep_attempts < :attempts
                                   AND requested_at < now() - make_interval(secs => :retryAfterSeconds * (sweep_attempts + 1))
                                 FOR UPDATE SKIP LOCKED)
            RETURNING *""")
    List<PaymentJpaEntity> sweep(@Param("retryAfterSeconds") double retryAfterSeconds, @Param("attempts") int attempts);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE payment SET payment_status = 'SETTLED', settled_at = now()
            WHERE payment_id = :paymentId AND payment_status = 'PAID'""")
    int settle(@Param("paymentId") long paymentId);
}
