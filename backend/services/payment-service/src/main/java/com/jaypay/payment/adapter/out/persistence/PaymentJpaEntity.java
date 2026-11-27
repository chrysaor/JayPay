package com.jaypay.payment.adapter.out.persistence;

import com.jaypay.payment.domain.PaymentStatus;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "payment", check = {
        @CheckConstraint(name = "payment_price_positive", constraint = "request_price > 0"),
        @CheckConstraint(name = "payment_fee_rate_in_range", constraint = "fee_rate_basis_points BETWEEN 0 AND 10000"),
        @CheckConstraint(name = "payment_fee_and_payout_make_the_price", constraint = "fee >= 0 AND payout >= 0 AND fee + payout = request_price")
})
@Getter
@NoArgsConstructor
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(nullable = false)
    private Long requestMembershipId;

    @Column(nullable = false)
    private Long franchiseId;

    @Column(nullable = false)
    private long requestPrice;

    @Column(nullable = false)
    private int feeRateBasisPoints;

    @Column(nullable = false)
    private long fee;

    @Column(nullable = false)
    private long payout;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant approvedAt;

    private Instant settledAt;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int sweepAttempts;
}
