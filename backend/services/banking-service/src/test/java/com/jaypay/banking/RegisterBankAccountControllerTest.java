package com.jaypay.banking;

import com.jayway.jsonpath.JsonPath;
import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterBankAccountControllerTest {

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandGateway commandGateway;

    @MockitoBean
    private GetMembershipPort getMembershipPort;

    @Test
    void registersBankAccountForValidMembership() throws Exception {
        when(getMembershipPort.getMembership("1")).thenReturn(new MembershipStatus("1", true));

        mockMvc.perform(post("/banking/account/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "1", "bankName": "Shinhan bank", "bankAccountNumber": "12345", "valid": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredBankAccountId").isNotEmpty())
                .andExpect(jsonPath("$.membershipId").value("1"))
                .andExpect(jsonPath("$.bankName").value("Shinhan bank"))
                .andExpect(jsonPath("$.bankAccountNumber").value("12345"))
                .andExpect(jsonPath("$.linkedStatusIsValid").value(true))
                .andExpect(jsonPath("$.aggregateIdentifier").isNotEmpty());
    }

    @Test
    void findsTheRegisteredBankAccountOfAMembership() throws Exception {
        when(getMembershipPort.getMembership("3")).thenReturn(new MembershipStatus("3", true));
        mockMvc.perform(post("/banking/account/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "3", "bankName": "Woori bank", "bankAccountNumber": "13579", "valid": true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/banking/account/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredBankAccountId").isNotEmpty())
                .andExpect(jsonPath("$.membershipId").value("3"))
                .andExpect(jsonPath("$.bankName").value("Woori bank"))
                .andExpect(jsonPath("$.bankAccountNumber").value("13579"))
                .andExpect(jsonPath("$.linkedStatusIsValid").value(true))
                .andExpect(jsonPath("$.aggregateIdentifier").isNotEmpty());
    }

    @Test
    void reportsExternalBankAccountInfo() throws Exception {
        mockMvc.perform(post("/banking/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankName": "Hana bank", "bankAccountNumber": "24680"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Hana bank"))
                .andExpect(jsonPath("$.bankAccountNumber").value("24680"))
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void registeredBankAccountCanBeCheckedByTheRechargingSaga() throws Exception {
        when(getMembershipPort.getMembership("2")).thenReturn(new MembershipStatus("2", true));

        String response = mockMvc.perform(post("/banking/account/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"membershipId": "2", "bankName": "KB bank", "bankAccountNumber": "67890", "valid": true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String aggregateIdentifier = JsonPath.read(response, "$.aggregateIdentifier");

        String rechargeId = UUID.randomUUID().toString();
        assertThatCode(() -> commandGateway.sendAndWait(new CheckRegisteredBankAccountCommand(
                aggregateIdentifier, rechargeId, "2", "check-1", "KB bank", "67890", 1000)))
                .doesNotThrowAnyException();

        List<String> steps = KafkaTestBroker.awaitValues("jaypay.flow.events", rechargeId, found -> !found.isEmpty(), Duration.ofSeconds(20));
        assertThat(steps).hasSize(1);
        JsonNode step = new ObjectMapper().readTree(steps.getFirst());
        assertThat(step.get("step").asText()).isEqualTo("BANK_ACCOUNT_CHECKED");
        assertThat(step.get("outcome").asText()).isEqualTo("SUCCEEDED");
        assertThat(step.get("service").asText()).isEqualTo("banking-service");
        assertThat(step.get("amount").asLong()).isEqualTo(1000);
        assertThat(step.toString()).doesNotContain("67890", "KB bank");
    }
}
