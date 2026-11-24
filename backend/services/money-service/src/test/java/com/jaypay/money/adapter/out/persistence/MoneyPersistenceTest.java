package com.jaypay.money.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MoneyPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataMemberMoneyRepository memberMoneyRepository;

    @Autowired
    private SpringDataMoneyChangingRequestRepository moneyChangingRequestRepository;

    @Test
    void findsMemberMoneyByMembershipIds() {
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(1L, 1000, "aggregate-1"));
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(2L, 2000, "aggregate-2"));
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(3L, 3000, "aggregate-3"));
        entityManager.clear();

        assertThat(memberMoneyRepository.findByMembershipId(2L))
                .extracting(MemberMoneyJpaEntity::getMembershipId, MemberMoneyJpaEntity::getBalance, MemberMoneyJpaEntity::getAggregateIdentifier)
                .containsExactly(tuple(2L, 2000, "aggregate-2"));
        assertThat(memberMoneyRepository.fineMemberMoneyListByMembershipIds(List.of(1L, 3L)))
                .extracting(MemberMoneyJpaEntity::getBalance)
                .containsExactlyInAnyOrder(1000, 3000);
    }

    @Test
    void storesMoneyChangingRequestWithTimestamp() {
        Timestamp requestedAt = new Timestamp(1_700_000_000_123L);
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Long id = moneyChangingRequestRepository.saveAndFlush(new MoneyChangingRequestJpaEntity("4", 0, 500, requestedAt, 1, uuid)).getMoneyChangingRequestId();
        entityManager.clear();

        MoneyChangingRequestJpaEntity found = moneyChangingRequestRepository.findById(id).orElseThrow();

        assertThat(found)
                .extracting(
                        MoneyChangingRequestJpaEntity::getTargetMembershipId,
                        MoneyChangingRequestJpaEntity::getMoneyChangingType,
                        MoneyChangingRequestJpaEntity::getMoneyAmount,
                        MoneyChangingRequestJpaEntity::getChangingMoneyStatus,
                        MoneyChangingRequestJpaEntity::getUuid)
                .containsExactly("4", 0, 500, 1, uuid.toString());
        assertThat(found.getTimestamp().getTime()).isEqualTo(requestedAt.getTime());
    }
}
