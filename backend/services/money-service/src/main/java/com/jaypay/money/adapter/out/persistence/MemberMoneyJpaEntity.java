package com.jaypay.money.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "wallet", check = @CheckConstraint(name = "wallet_balance_not_negative", constraint = "balance >= 0"))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemberMoneyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberMoneyId;
    @Column(nullable = false, unique = true)
    private Long membershipId;
    @Column(nullable = false)
    private long balance;
    @Column(nullable = false)
    private String aggregateIdentifier;

    public MemberMoneyJpaEntity(Long membershipId, long balance, String aggregateIdentifier) {
        this.membershipId = membershipId;
        this.balance = balance;
        this.aggregateIdentifier = aggregateIdentifier;
    }

}
