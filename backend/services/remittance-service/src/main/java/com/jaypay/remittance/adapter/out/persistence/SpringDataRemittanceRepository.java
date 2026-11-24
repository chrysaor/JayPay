package com.jaypay.remittance.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataRemittanceRepository extends JpaRepository<RemittanceJpaEntity, Long> {
}
