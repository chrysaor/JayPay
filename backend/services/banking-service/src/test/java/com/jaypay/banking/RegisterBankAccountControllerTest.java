package com.jaypay.banking;

import com.jayway.jsonpath.JsonPath;
import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterBankAccountControllerTest {

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

        assertThatCode(() -> commandGateway.sendAndWait(new CheckRegisteredBankAccountCommand(
                aggregateIdentifier, "recharge-1", "2", "check-1", "KB bank", "67890", 1000)))
                .doesNotThrowAnyException();
    }
}
