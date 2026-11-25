package com.jaypay.money.adapter.out.persistence;

import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingStatus;
import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;


@Entity
@Table(name = "wallet_change")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoneyChangingRequestJpaEntity {

    @Id
    private String requestId;

    @Column(nullable = false)
    private Long membershipId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MoneyChangingType changeType;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MoneyChangingStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

}
