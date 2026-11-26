package com.jaypay.banking.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SpringDataBankTransferRepository extends JpaRepository<BankTransferJpaEntity, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO bank_transfer (request_id, from_bank_name, from_bank_account_number, to_bank_name, to_bank_account_number,
                                       amount, status, requested_at)
            VALUES (:requestId, :fromBankName, :fromBankAccountNumber, :toBankName, :toBankAccountNumber, :amount, :status, now())
            ON CONFLICT (request_id) DO NOTHING""")
    int claim(@Param("requestId") String requestId,
              @Param("fromBankName") String fromBankName,
              @Param("fromBankAccountNumber") String fromBankAccountNumber,
              @Param("toBankName") String toBankName,
              @Param("toBankAccountNumber") String toBankAccountNumber,
              @Param("amount") long amount,
              @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BankTransferJpaEntity t WHERE t.requestId = :requestId")
    Optional<BankTransferJpaEntity> findForUpdate(@Param("requestId") String requestId);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE bank_transfer SET status = :status WHERE request_id = :requestId AND status = 'PENDING'")
    int settle(@Param("requestId") String requestId, @Param("status") String status);
}
