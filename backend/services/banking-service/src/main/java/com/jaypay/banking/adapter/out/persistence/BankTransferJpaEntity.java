package com.jaypay.banking.adapter.out.persistence;

import com.jaypay.banking.domain.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "bank_transfer")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BankTransferJpaEntity {

    @Id
    @Column(length = 64)
    private String requestId;

    private String fromBankName;

    @ToString.Exclude
    private String fromBankAccountNumber;

    private String toBankName;

    @ToString.Exclude
    private String toBankAccountNumber;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    @Column(nullable = false)
    private Instant requestedAt;
}
