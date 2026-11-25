package com.jaypay.money.adapter.in.web;

import com.jaypay.money.adapter.axon.event.RechargingRequestCreatedEvent;
import com.jaypay.money.adapter.out.persistence.SpringDataMemberMoneyRepository;
import com.jaypay.money.application.port.in.ChangeMoneyUseCase;
import com.jaypay.money.domain.RequestId;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.sun.net.httpserver.HttpServer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncreaseMoneyTest {

    private static final Map<String, String> RESPONSES = Map.of(
            "/memberships/7", member("7", true),
            "/memberships/8", member("8", false),
            "/banking/account/9", "{\"registeredBankAccountId\":\"3\",\"membershipId\":\"9\",\"bankName\":\"jay-bank\","
                    + "\"bankAccountNumber\":\"1002-9\",\"linkedStatusIsValid\":true,\"aggregateIdentifier\":\"bank-aggregate-9\"}");

    private static final HttpServer OTHER_SERVICES = startOtherServices();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpringDataMemberMoneyRepository memberMoneyRepository;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private ChangeMoneyUseCase changeMoneyUseCase;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + OTHER_SERVICES.getAddress().getPort();
        registry.add("service.membership.url", () -> url);
        registry.add("service.banking.url", () -> url);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @AfterAll
    static void stopOtherServices() {
        OTHER_SERVICES.stop(0);
    }

    @Test
    void chargesTheWalletOfAValidMember() throws Exception {
        mockMvc.perform(post("/money/increase").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"charge-7\",\"targetMembershipId\":\"7\",\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyChangingRequestId").value("charge-7"))
                .andExpect(jsonPath("$.moneyChangingType").value(1))
                .andExpect(jsonPath("$.moneyChangingResultStatus").value(1))
                .andExpect(jsonPath("$.amount").value(1000));

        mockMvc.perform(post("/money/member-money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMembershipIds\":[\"7\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].membershipId").value("7"))
                .andExpect(jsonPath("$[0].memberMoneyId").value(isA(String.class)))
                .andExpect(jsonPath("$[0].balance").value(1000));
    }

    @Test
    void reportsAFailedChargeForAnInvalidMemberAndLeavesTheWalletAlone() throws Exception {
        mockMvc.perform(post("/money/increase").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"charge-8\",\"targetMembershipId\":\"8\",\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyChangingRequestId").value("charge-8"))
                .andExpect(jsonPath("$.moneyChangingType").value(1))
                .andExpect(jsonPath("$.moneyChangingResultStatus").value(2))
                .andExpect(jsonPath("$.amount").value(1000));

        mockMvc.perform(post("/money/member-money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMembershipIds\":[\"8\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void recordsARechargingRequestCarryingTheMembersRegisteredBankAccount() throws Exception {
        mockMvc.perform(post("/money/create-member-money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"9\"}"))
                .andExpect(status().isOk());
        String aggregateIdentifier = memberMoneyRepository.findByMembershipId(9L).orElseThrow().getAggregateIdentifier();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/money/increase-eda").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requestId\":\"recharge-9\",\"targetMembershipId\":\"9\",\"amount\":500}"))
                    .andExpect(status().isOk());
        }

        List<Object> rechargings = eventStore.readEvents(aggregateIdentifier).asStream()
                .<Object>map(event -> event.getPayload())
                .filter(RechargingRequestCreatedEvent.class::isInstance)
                .toList();
        assertThat(rechargings).hasSize(1);
        RechargingRequestCreatedEvent recharging = (RechargingRequestCreatedEvent) rechargings.getFirst();
        assertThat(recharging.rechargingRequestId()).isEqualTo("recharge-9");
        assertThat(List.of(recharging.membershipId(), recharging.registeredBankAccountAggregateIdentifier(),
                recharging.bankName(), recharging.bankAccountNumber()))
                .containsExactly("9", "bank-aggregate-9", "jay-bank", "1002-9");
        assertThat(recharging.amount()).isEqualTo(500);

        assertThat(changeMoneyUseCase.finishRecharging(new RequestId("recharge-9"))).isTrue();
        assertThat(changeMoneyUseCase.finishRecharging(new RequestId("recharge-9"))).isTrue();
        assertThat(changeMoneyUseCase.finishRecharging(new RequestId("never-requested"))).isFalse();
        assertThat(memberMoneyRepository.findByMembershipId(9L).orElseThrow().getBalance()).isEqualTo(500);
    }

    private static String member(String membershipId, boolean valid) {
        return "{\"membershipId\":\"" + membershipId + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                + "\"address\":\"Seocho\",\"valid\":" + valid + ",\"corp\":false,\"refreshToken\":\"\"}";
    }

    private static HttpServer startOtherServices() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String response = RESPONSES.get(exchange.getRequestURI().getPath());
                byte[] body = (response == null ? "{\"status\":404}" : response).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response == null ? 404 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
