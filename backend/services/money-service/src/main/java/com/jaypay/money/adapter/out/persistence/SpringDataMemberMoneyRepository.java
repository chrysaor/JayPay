package com.jaypay.money.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SpringDataMemberMoneyRepository extends JpaRepository<MemberMoneyJpaEntity, Long> {

    Optional<MemberMoneyJpaEntity> findByMembershipId(Long membershipId);

    List<MemberMoneyJpaEntity> findByMembershipIdIn(List<Long> membershipIds);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO wallet (membership_id, balance, aggregate_identifier) VALUES (:membershipId, :amount, '')
            ON CONFLICT (membership_id) DO UPDATE SET balance = wallet.balance + EXCLUDED.balance""")
    void increaseBalance(@Param("membershipId") long membershipId, @Param("amount") long amount);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE wallet SET balance = balance - :amount WHERE membership_id = :membershipId AND balance >= :amount""")
    int decreaseBalance(@Param("membershipId") long membershipId, @Param("amount") long amount);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO wallet (membership_id, balance, aggregate_identifier) VALUES (:membershipId, 0, :aggregateIdentifier)
            ON CONFLICT (membership_id) DO UPDATE SET aggregate_identifier = EXCLUDED.aggregate_identifier""")
    void openWallet(@Param("membershipId") long membershipId, @Param("aggregateIdentifier") String aggregateIdentifier);

}
