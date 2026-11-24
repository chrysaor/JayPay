package com.jaypay.remittance;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestRemittanceTest {

    private static final AtomicInteger membershipIds = new AtomicInteger(1000);
    private static final Map<String, Integer> balances = new ConcurrentHashMap<>();
    private static final List<String> firmBankingRequests = new CopyOnWriteArrayList<>();
    private static final HttpServer jaypay = startFakeJayPay();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void pointAtFakeJayPay(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + jaypay.getAddress().getPort();
        registry.add("service.membership.url", () -> url);
        registry.add("service.money.url", () -> url);
        registry.add("service.banking.url", () -> url);
    }

    @AfterAll
    static void stopFakeJayPay() {
        jaypay.stop(0);
    }

    @Test
    void remitsMoneyBetweenMembers() throws Exception {
        String from = memberWithBalance(20000);
        String to = memberWithBalance(0);

        mockMvc.perform(post("/remittance/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMembershipId": "%s", "toMembershipId": "%s", "remittanceType": 0, "amount": 5000}
                                """.formatted(from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remittanceFromMembershipId").value(from))
                .andExpect(jsonPath("$.amount").value(5000))
                .andExpect(jsonPath("$.remittanceStatus").value("success"));

        assertThat(balances).containsEntry(from, 15000).containsEntry(to, 5000);
    }

    @Test
    void rechargesTheSenderInUnitsOfTenThousandWhenTheBalanceIsShort() throws Exception {
        String from = memberWithBalance(3000);
        String to = memberWithBalance(0);

        mockMvc.perform(post("/remittance/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMembershipId": "%s", "toMembershipId": "%s", "remittanceType": 0, "amount": 15000}
                                """.formatted(from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remittanceStatus").value("success"));

        assertThat(balances).containsEntry(from, 8000).containsEntry(to, 15000);
    }

    @Test
    void remitsMoneyToABankAccount() throws Exception {
        String from = memberWithBalance(20000);

        mockMvc.perform(post("/remittance/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMembershipId": "%s", "toBankName": "Jay Bank", "toBankAccountNumber": "1002-%s", "remittanceType": 1, "amount": 7000}
                                """.formatted(from, from)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toBankName").value("Jay Bank"))
                .andExpect(jsonPath("$.remittanceStatus").value("success"));

        assertThat(firmBankingRequests).anySatisfy(request -> {
            DocumentContext json = JsonPath.parse(request);
            assertThat(json.<String>read("$.toBankName")).isEqualTo("Jay Bank");
            assertThat(json.<String>read("$.toBankAccountNumber")).isEqualTo("1002-" + from);
            assertThat(json.<Integer>read("$.moneyAmount")).isEqualTo(7000);
        });
    }

    private static String memberWithBalance(int balance) {
        String membershipId = String.valueOf(membershipIds.incrementAndGet());
        balances.put(membershipId, balance);
        return membershipId;
    }

    private static HttpServer startFakeJayPay() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            route(server, "GET", "/memberships/", (path, body) -> """
                    {"membershipId": "%s", "name": "member", "email": "member@example.com", "address": "seoul", "valid": true, "corp": false}
                    """.formatted(path.substring("/memberships/".length())));
            route(server, "POST", "/money/member-money", (path, body) -> {
                String membershipId = JsonPath.read(body, "$.targetMembershipIds[0]");
                return """
                        [{"memberMoneyId": "1", "membershipId": "%s", "balance": %d}]
                        """.formatted(membershipId, balances.getOrDefault(membershipId, 0));
            });
            route(server, "POST", "/money/increase", (path, body) -> {
                DocumentContext request = JsonPath.parse(body);
                int amount = request.read("$.amount");
                balances.merge(request.read("$.targetMembershipId"), amount, Integer::sum);
                return """
                        {"moneyChangingRequestId": "1", "moneyChangingType": 1, "moneyChangingResultStatus": 1, "amount": %d}
                        """.formatted(amount);
            });
            route(server, "POST", "/banking/info", (path, body) -> {
                DocumentContext request = JsonPath.parse(body);
                return """
                        {"bankName": "%s", "bankAccountNumber": "%s", "valid": true}
                        """.formatted(request.<String>read("$.bankName"), request.<String>read("$.bankAccountNumber"));
            });
            route(server, "POST", "/banking/firmbanking/request", (path, body) -> {
                firmBankingRequests.add(body);
                return """
                        {"firmBankingRequestId": "1", "moneyAmount": %d, "firmBankingStatus": 1}
                        """.formatted(JsonPath.<Integer>read(body, "$.moneyAmount"));
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void route(HttpServer server, String method, String path, BiFunction<String, String, String> response) {
        server.createContext(path, exchange -> {
            try (exchange) {
                if (!exchange.getRequestMethod().equals(method)) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] json = response.apply(exchange.getRequestURI().getPath(), body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
            }
        });
    }
}
