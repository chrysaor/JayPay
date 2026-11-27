package com.jaypay.banking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataRegisteredBankAccountRepository extends JpaRepository<RegisteredBankAccountJpaEntity, Long> {

    Optional<RegisteredBankAccountJpaEntity> findFirstByMembershipIdOrderByRegisteredBankAccountIdDesc(String membershipId);

}
