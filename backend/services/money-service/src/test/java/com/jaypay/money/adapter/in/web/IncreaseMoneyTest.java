package com.jaypay.money.adapter.in.web;

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
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncreaseMoneyTest {

    private static final Map<String, String> MEMBERS = Map.of(
            "/memberships/7", member("7", true),
            "/memberships/8", member("8", false));

    private static final HttpServer MEMBERSHIP_SERVICE = startMembershipService();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void membershipService(DynamicPropertyRegistry registry) {
        registry.add("service.membership.url", () -> "http://127.0.0.1:" + MEMBERSHIP_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stopMembershipService() {
        MEMBERSHIP_SERVICE.stop(0);
    }

    @Test
    void chargesTheWalletOfAValidMember() throws Exception {
        mockMvc.perform(post("/money/increase").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMembershipId\":\"7\",\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyChangingRequestId").value(isA(String.class)))
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
                        .content("{\"targetMembershipId\":\"8\",\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyChangingRequestId").value(isA(String.class)))
                .andExpect(jsonPath("$.moneyChangingType").value(1))
                .andExpect(jsonPath("$.moneyChangingResultStatus").value(2))
                .andExpect(jsonPath("$.amount").value(1000));

        mockMvc.perform(post("/money/member-money").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMembershipIds\":[\"8\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private static String member(String membershipId, boolean valid) {
        return "{\"membershipId\":\"" + membershipId + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                + "\"address\":\"Seocho\",\"valid\":" + valid + ",\"corp\":false,\"refreshToken\":\"\"}";
    }

    private static HttpServer startMembershipService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String member = MEMBERS.get(exchange.getRequestURI().getPath());
                byte[] body = (member == null ? "{\"status\":404}" : member).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(member == null ? 404 : 200, body.length);
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
