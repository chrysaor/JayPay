package com.jaypay.remittance.adapter.out.persistence;

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
class RemittancePersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataRemittanceRequestRepository remittanceRequestRepository;

    @Autowired
    private SpringDataRemittanceRepository remittanceRepository;

    @Test
    void storesRemittanceRequest() {
        Long id = remittanceRequestRepository.saveAndFlush(RemittanceRequestJpaEntity.builder()
                .fromMembershipId("1")
                .toMembershipId("2")
                .toBankName("Shinhan bank")
                .toBankAccountNumber("12345")
                .remittanceType(1)
                .amount(150000)
                .remittanceStatus("success")
                .build()).getRemittanceRequestId();
        entityManager.clear();

        assertThat(remittanceRequestRepository.findById(id)).get()
                .extracting(
                        RemittanceRequestJpaEntity::getFromMembershipId,
                        RemittanceRequestJpaEntity::getToMembershipId,
                        RemittanceRequestJpaEntity::getToBankName,
                        RemittanceRequestJpaEntity::getToBankAccountNumber,
                        RemittanceRequestJpaEntity::getRemittanceType,
                        RemittanceRequestJpaEntity::getAmount,
                        RemittanceRequestJpaEntity::getRemittanceStatus)
                .containsExactly("1", "2", "Shinhan bank", "12345", 1, 150000, "success");
    }

    @Test
    void storesRemittance() {
        Long id = remittanceRepository.saveAndFlush(new RemittanceJpaEntity(null, "success")).getRemittanceId();
        entityManager.clear();

        assertThat(remittanceRepository.findById(id)).get()
                .extracting(RemittanceJpaEntity::getStatus)
                .isEqualTo("success");
    }
}
