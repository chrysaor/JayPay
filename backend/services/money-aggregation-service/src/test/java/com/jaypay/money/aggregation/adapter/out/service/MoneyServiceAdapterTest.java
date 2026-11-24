package com.jaypay.money.aggregation.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.money.aggregation.application.port.out.MemberMoney;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyServiceAdapterTest {

    private static final String MEMBER_MONEY =
            "[{\"memberMoneyId\":\"1\",\"membershipId\":\"7\",\"balance\":3000},"
                    + "{\"memberMoneyId\":\"2\",\"membershipId\":\"8\",\"balance\":1200}]";

    private final List<String> requests = new ArrayList<>();
    private HttpServer moneyService;
    private MoneyServiceAdapter adapter;

    @BeforeEach
    void startMoneyService() throws IOException {
        moneyService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        moneyService.createContext("/", exchange -> {
            boolean known = exchange.getRequestURI().getPath().equals("/money/member-money")
                    && exchange.getRequestMethod().equals("POST");
            if (known) {
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] body = (known ? MEMBER_MONEY : "{\"status\":404}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(known ? 200 : 404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        moneyService.start();
        adapter = new MoneyServiceAdapter(
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP),
                "http://127.0.0.1:" + moneyService.getAddress().getPort());
    }

    @AfterEach
    void stopMoneyService() {
        moneyService.stop(0);
    }

    @Test
    void readsTheMoneyOfTheRequestedMembers() {
        List<MemberMoney> memberMoney = adapter.getMoneySumByMembershipIds(List.of("7", "8"));

        assertThat(requests).containsExactly("{\"targetMembershipIds\":[\"7\",\"8\"]}");
        assertThat(memberMoney).containsExactly(new MemberMoney("7", 3000), new MemberMoney("8", 1200));
    }
}
