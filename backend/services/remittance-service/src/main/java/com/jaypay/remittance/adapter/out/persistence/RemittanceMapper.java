package com.jaypay.remittance.adapter.out.persistence;

import com.jaypay.remittance.domain.Remittance;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RemittanceMapper {
    public Remittance mapToDomainEntity(RemittanceJpaEntity remittanceJpaEntity, UUID uuid) {
        return Remittance.generateRemittance(

        );
    }
}
