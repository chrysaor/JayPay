package com.jaypay.banking.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BankingPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataRegisteredBankAccountRepository registeredBankAccountRepository;

    @Autowired
    private SpringDataFirmBankingRequestRepository firmBankingRequestRepository;

    @Test
    void findsRegisteredBankAccountByMembershipId() {
        registeredBankAccountRepository.saveAndFlush(new RegisteredBankAccountJpaEntity("7", "Shinhan bank", "12345", true, "aggregate-7"));
        entityManager.clear();

        assertThat(registeredBankAccountRepository.findByMembershipId("7"))
                .extracting(
                        RegisteredBankAccountJpaEntity::getMembershipId,
                        RegisteredBankAccountJpaEntity::getBankName,
                        RegisteredBankAccountJpaEntity::getBankAccountNumber,
                        RegisteredBankAccountJpaEntity::isLinkedStatusIsValid,
                        RegisteredBankAccountJpaEntity::getAggregateIdentifier)
                .containsExactly(tuple("7", "Shinhan bank", "12345", true, "aggregate-7"));
    }

    @Test
    void findsFirmBankingRequestByAggregateIdentifier() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        firmBankingRequestRepository.saveAndFlush(new FirmBankingRequestJpaEntity("Shinhan bank", "111", "KB bank", "222", 5000, 1, uuid, "aggregate-8"));
        entityManager.clear();

        assertThat(firmBankingRequestRepository.findByAggregateIdentifier("aggregate-8"))
                .extracting(
                        FirmBankingRequestJpaEntity::getFromBankName,
                        FirmBankingRequestJpaEntity::getFromBankAccountNumber,
                        FirmBankingRequestJpaEntity::getToBankName,
                        FirmBankingRequestJpaEntity::getToBankAccountNumber,
                        FirmBankingRequestJpaEntity::getMoneyAmount,
                        FirmBankingRequestJpaEntity::getFirmBankingStatus,
                        FirmBankingRequestJpaEntity::getUuid)
                .containsExactly(tuple("Shinhan bank", "111", "KB bank", "222", 5000, 1, uuid.toString()));
    }
}
