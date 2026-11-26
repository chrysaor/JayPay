package com.jaypay.settlement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPayoutAttemptRepository extends JpaRepository<PayoutAttemptJpaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO payout_attempt (payment_id, attempt, bank_name, bank_account_number)
            VALUES (:paymentId, 1, :bankName, :bankAccountNumber)
            ON CONFLICT (payment_id) DO NOTHING""")
    int claim(@Param("paymentId") long paymentId,
              @Param("bankName") String bankName,
              @Param("bankAccountNumber") String bankAccountNumber);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE payout_attempt SET attempt = :refused + 1, bank_name = :bankName, bank_account_number = :bankAccountNumber
            WHERE payment_id = :paymentId AND attempt = :refused""")
    int advance(@Param("paymentId") long paymentId,
                @Param("refused") int refused,
                @Param("bankName") String bankName,
                @Param("bankAccountNumber") String bankAccountNumber);
}
