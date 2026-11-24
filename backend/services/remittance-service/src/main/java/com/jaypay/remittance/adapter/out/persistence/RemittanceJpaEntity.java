package com.jaypay.remittance.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "remittance")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RemittanceJpaEntity {
    @Id
    @GeneratedValue
    private Long remittanceId;

    private String status;

    public RemittanceJpaEntity(String fromBankName, String fromBankAccountNumber, String toBankName, String toBankAccountNumber, int moneyAmount, int firmbankingStatus, UUID uuid) {

    }

}