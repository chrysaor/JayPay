package com.jaypay.settlement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payout_attempt")
@Getter
@NoArgsConstructor
public class PayoutAttemptJpaEntity {

    @Id
    private long paymentId;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String bankAccountNumber;
}
