package com.jaypay.remittance.adapter.out.persistence;

import com.jaypay.remittance.domain.RemittanceStatus;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "remittance",
        indexes = {
                @Index(name = "remittance_sender", columnList = "from_membership_id"),
                @Index(name = "remittance_receiver", columnList = "to_membership_id")
        },
        check = {
                @CheckConstraint(name = "remittance_amount_positive", constraint = "amount > 0"),
                @CheckConstraint(name = "remittance_receiver_matches_type", constraint = """
                        (remittance_type = 'MEMBER' AND to_membership_id IS NOT NULL AND to_membership_id <> from_membership_id
                            AND to_bank_name IS NULL AND to_bank_account_number IS NULL)
                        OR (remittance_type = 'BANK_ACCOUNT' AND to_membership_id IS NULL
                            AND to_bank_name IS NOT NULL AND to_bank_account_number IS NOT NULL)""")
        })
@Getter
@NoArgsConstructor
public class RemittanceJpaEntity {

    @Id
    private UUID remittanceId;

    @Column(nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(nullable = false)
    private Long fromMembershipId;

    @Column(nullable = false, length = 16)
    private String remittanceType;

    private Long toMembershipId;

    private String toBankName;

    private String toBankAccountNumber;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RemittanceStatus remittanceStatus;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
