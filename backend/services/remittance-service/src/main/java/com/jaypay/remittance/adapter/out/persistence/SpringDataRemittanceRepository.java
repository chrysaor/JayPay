package com.jaypay.remittance.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRemittanceRepository extends JpaRepository<RemittanceJpaEntity, UUID> {

    Optional<RemittanceJpaEntity> findByRequestId(String requestId);

    List<RemittanceJpaEntity> findByFromMembershipIdOrToMembershipIdOrderByRequestedAtDesc(Long fromMembershipId, Long toMembershipId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO remittance (remittance_id, request_id, from_membership_id, remittance_type, to_membership_id, to_bank_name,
                                    to_bank_account_number, amount, remittance_status, requested_at, updated_at)
            VALUES (:remittanceId, :requestId, :fromMembershipId, :remittanceType, CAST(:toMembershipId AS bigint),
                    CAST(:toBankName AS varchar), CAST(:toBankAccountNumber AS varchar), :amount, 'REQUESTED', now(), now())
            ON CONFLICT (request_id) DO NOTHING""")
    int claim(@Param("remittanceId") UUID remittanceId, @Param("requestId") String requestId,
              @Param("fromMembershipId") long fromMembershipId, @Param("remittanceType") String remittanceType,
              @Param("toMembershipId") Long toMembershipId, @Param("toBankName") String toBankName,
              @Param("toBankAccountNumber") String toBankAccountNumber, @Param("amount") long amount);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE remittance SET remittance_status = :next, updated_at = now()
            WHERE remittance_id = :remittanceId AND remittance_status = :current""")
    int advance(@Param("remittanceId") UUID remittanceId, @Param("current") String current, @Param("next") String next);
}
