package com.jaypay.banking;

import com.jaypay.banking.adapter.out.persistence.FirmBankingRequestJpaEntity;
import com.jaypay.banking.adapter.out.persistence.SpringDataFirmBankingRequestRepository;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirmBankingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private SpringDataFirmBankingRequestRepository firmBankingRequestRepository;

    @Test
    void requestsFirmBankingAndReturnsTheSucceededRequest() throws Exception {
        mockMvc.perform(post("/banking/firmbanking/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromBankName": "Shinhan bank", "fromBankAccountNumber": "111", "toBankName": "KB bank", "toBankAccountNumber": "222", "moneyAmount": 5000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmBankingRequestId").isNotEmpty())
                .andExpect(jsonPath("$.fromBankName").value("Shinhan bank"))
                .andExpect(jsonPath("$.fromBankAccountNumber").value("111"))
                .andExpect(jsonPath("$.toBankName").value("KB bank"))
                .andExpect(jsonPath("$.toBankAccountNumber").value("222"))
                .andExpect(jsonPath("$.moneyAmount").value(5000))
                .andExpect(jsonPath("$.firmBankingStatus").value(1))
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andExpect(jsonPath("$.aggregateIdentifier").value(""));
    }

    @Test
    void requestsFirmBankingThroughTheAggregateAndUpdatesItsStatus() throws Exception {
        mockMvc.perform(post("/banking/firmbanking/request-eda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromBankName": "Eda bank", "fromBankAccountNumber": "333", "toBankName": "KB bank", "toBankAccountNumber": "444", "moneyAmount": 7000}
                                """))
                .andExpect(status().isOk());

        FirmBankingRequestJpaEntity requested = firmBankingRequestRepository.findAll().stream()
                .filter(entity -> entity.getFromBankName().equals("Eda bank"))
                .findFirst()
                .orElseThrow();
        assertThat(requested.getAggregateIdentifier()).isNotBlank();
        assertThat(requested.getFirmBankingStatus()).isEqualTo(1);

        mockMvc.perform(put("/banking/firmbanking/update-eda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firmBankingRequestAggregateIdentifier": "%s", "status": 2}
                                """.formatted(requested.getAggregateIdentifier())))
                .andExpect(status().isOk());

        assertThat(firmBankingRequestRepository.findByAggregateIdentifier(requested.getAggregateIdentifier()))
                .extracting(FirmBankingRequestJpaEntity::getFirmBankingStatus)
                .containsExactly(2);
    }

    @Test
    void recordsTheRechargingSagaFirmBankingAsATransferIntoTheCompanyAccount() {
        String aggregateIdentifier = commandGateway.sendAndWait(new RequestFirmBankingCommand(
                "firmbanking-1", "saga-aggregate-1", "recharge-1", "1", "Shinhan bank", "555", "KB bank", "666", 3000));

        assertThat(aggregateIdentifier).isEqualTo("saga-aggregate-1");
        assertThat(firmBankingRequestRepository.findByAggregateIdentifier("saga-aggregate-1"))
                .extracting(
                        FirmBankingRequestJpaEntity::getFromBankName,
                        FirmBankingRequestJpaEntity::getFromBankAccountNumber,
                        FirmBankingRequestJpaEntity::getToBankName,
                        FirmBankingRequestJpaEntity::getToBankAccountNumber,
                        FirmBankingRequestJpaEntity::getMoneyAmount,
                        FirmBankingRequestJpaEntity::getFirmBankingStatus)
                .containsExactly(tuple("KB bank", "666", "jaypay-company", "123-333-9999", 3000, 0));
    }

    @Test
    void recordsTheRollbackAsATransferOutOfTheCompanyAccount() {
        String aggregateIdentifier = commandGateway.sendAndWait(new RollbackFirmBankingRequestCommand(
                "rollback-1", "saga-aggregate-2", "recharge-2", "1", "Woori bank", "777", 4000));

        assertThat(firmBankingRequestRepository.findByAggregateIdentifier(aggregateIdentifier))
                .extracting(
                        FirmBankingRequestJpaEntity::getFromBankName,
                        FirmBankingRequestJpaEntity::getFromBankAccountNumber,
                        FirmBankingRequestJpaEntity::getToBankName,
                        FirmBankingRequestJpaEntity::getToBankAccountNumber,
                        FirmBankingRequestJpaEntity::getMoneyAmount,
                        FirmBankingRequestJpaEntity::getFirmBankingStatus)
                .containsExactly(tuple("jaypay-company", "123-333-9999", "Woori bank", "777", 4000, 0));
    }
}
