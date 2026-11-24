package com.jaypay.membership.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MembershipPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    @Test
    void findsMembershipByIdAndAddress() {
        Long id = membershipRepository.saveAndFlush(new MembershipJpaEntity("kim", "encrypted-email", "seoul", true, false, "refresh")).getMembershipId();
        entityManager.clear();

        assertThat(membershipRepository.findById(id)).get()
                .extracting(
                        MembershipJpaEntity::getName,
                        MembershipJpaEntity::getEmail,
                        MembershipJpaEntity::getAddress,
                        MembershipJpaEntity::isValid,
                        MembershipJpaEntity::isCorp,
                        MembershipJpaEntity::getRefreshToken)
                .containsExactly("kim", "encrypted-email", "seoul", true, false, "refresh");
        assertThat(membershipRepository.findByAddress("seoul"))
                .extracting(MembershipJpaEntity::getMembershipId)
                .containsExactly(id);
    }
}
