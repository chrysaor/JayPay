package com.jaypay.money.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MoneyPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataMemberMoneyRepository memberMoneyRepository;

    @Test
    void findsMemberMoneyByMembershipIds() {
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(1L, 1000, "aggregate-1"));
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(2L, 2000, "aggregate-2"));
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(3L, 3000, "aggregate-3"));
        entityManager.clear();

        assertThat(memberMoneyRepository.findByMembershipId(2L))
                .get()
                .extracting(MemberMoneyJpaEntity::getMembershipId, MemberMoneyJpaEntity::getBalance, MemberMoneyJpaEntity::getAggregateIdentifier)
                .containsExactly(2L, 2000L, "aggregate-2");
        assertThat(memberMoneyRepository.findByMembershipIdIn(List.of(1L, 3L)))
                .extracting(MemberMoneyJpaEntity::getMembershipId, MemberMoneyJpaEntity::getBalance)
                .containsExactlyInAnyOrder(tuple(1L, 1000L), tuple(3L, 3000L));
    }

    @Test
    void refusesASecondWalletForAMember() {
        memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(4L, 0, "aggregate-4"));

        assertThatThrownBy(() -> memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(4L, 0, "aggregate-4b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesANegativeBalance() {
        assertThatThrownBy(() -> memberMoneyRepository.saveAndFlush(new MemberMoneyJpaEntity(5L, -1, "aggregate-5")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void opensAWalletOutsideAnyTransaction() {
        memberMoneyRepository.openWallet(6L, "aggregate-6");

        assertThat(memberMoneyRepository.findByMembershipId(6L))
                .get()
                .extracting(MemberMoneyJpaEntity::getBalance, MemberMoneyJpaEntity::getAggregateIdentifier)
                .containsExactly(0L, "aggregate-6");
        memberMoneyRepository.deleteAll();
    }
}
