package com.jaypay.money.aggregation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GetMoneySumControllerTest {

    private static final Set<String> FAILING = ConcurrentHashMap.newKeySet();
    private static final HttpServer JAYPAY = startFakeJayPay();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + JAYPAY.getAddress().getPort();
        registry.add("service.membership.url", () -> url);
        registry.add("service.money.url", () -> url);
    }

    @BeforeEach
    void everyServiceAnswers() {
        FAILING.clear();
    }

    @Test
    void sumsTheBalancesOfTheMembersAtAnAddress() throws Exception {
        sumAt("Seocho").andExpect(status().isOk()).andExpect(content().string("4500"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"membership", "money"})
    void aSumWhileAServiceItReadsFailsWith5xxAnswers503AndTheSumAfterTheOutageIsWhole(String failing) throws Exception {
        FAILING.add(failing);

        sumAt("Seocho")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("MONEY_SUM_NOT_CONFIRMED"));

        FAILING.remove(failing);
        sumAt("Seocho").andExpect(status().isOk()).andExpect(content().string("4500"));
    }

    private ResultActions sumAt(String address) throws Exception {
        return mockMvc.perform(post("/money/aggregation/get-money-sum-by-address")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"" + address + "\"}"));
    }

    private static HttpServer startFakeJayPay() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/memberships/address/Seocho", exchange -> respond(exchange, "membership",
                    "[{\"membershipId\":\"7\",\"name\":\"Payer\",\"email\":\"payer@example.com\",\"address\":\"Seocho\",\"valid\":true,\"corp\":false},"
                            + "{\"membershipId\":\"8\",\"name\":\"Payee\",\"email\":\"payee@example.com\",\"address\":\"Seocho\",\"valid\":true,\"corp\":true}]"));
            server.createContext("/money/member-money", exchange -> respond(exchange, "money",
                    "[{\"memberMoneyId\":\"1\",\"membershipId\":\"7\",\"balance\":3000},{\"memberMoneyId\":\"2\",\"membershipId\":\"8\",\"balance\":1500}]"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, String service, String json) throws IOException {
        exchange.getRequestBody().readAllBytes();
        if (FAILING.contains(service)) {
            exchange.sendResponseHeaders(503, -1);
        } else {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
