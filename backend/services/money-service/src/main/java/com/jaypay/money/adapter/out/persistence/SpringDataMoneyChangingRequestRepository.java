package com.jaypay.money.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataMoneyChangingRequestRepository extends JpaRepository<MoneyChangingRequestJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO wallet_change (request_id, membership_id, change_type, amount, status, requested_at)
            VALUES (:requestId, :membershipId, :changeType, :amount, 'PENDING', now())
            ON CONFLICT (request_id) DO NOTHING""")
    int claim(@Param("requestId") String requestId, @Param("membershipId") long membershipId,
              @Param("changeType") String changeType, @Param("amount") long amount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MoneyChangingRequestJpaEntity r WHERE r.requestId = :requestId")
    Optional<MoneyChangingRequestJpaEntity> findForUpdate(@Param("requestId") String requestId);

}
