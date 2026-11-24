package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.port.out.RegisteredBankAccountAggregateIdentifier;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BankingServiceAdapterTest {

    private static final String BANKING_SERVICE_RESPONSE =
            "{\"registeredBankAccountId\":\"3\",\"membershipId\":\"franchise-9\",\"bankName\":\"jaybank\","
                    + "\"bankAccountNumber\":\"123-456-789\",\"linkedStatusIsValid\":true,\"aggregateIdentifier\":\"agg-1\"}";

    private HttpServer bankingService;
    private BankingServiceAdapter adapter;

    @BeforeEach
    void startBankingService() throws IOException {
        bankingService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bankingService.createContext("/", exchange -> {
            boolean known = exchange.getRequestURI().getPath().equals("/banking/account/franchise-9");
            byte[] body = (known ? BANKING_SERVICE_RESPONSE : "{\"status\":404}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(known ? 200 : 404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        bankingService.start();
        adapter = new BankingServiceAdapter(
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP),
                "http://127.0.0.1:" + bankingService.getAddress().getPort());
    }

    @AfterEach
    void stopBankingService() {
        bankingService.stop(0);
    }

    @Test
    void readsTheRegisteredAccountFromTheBankingService() {
        RegisteredBankAccountAggregateIdentifier account = adapter.getRegisteredBankAccount("franchise-9");

        assertThat(account.getMembershipId()).isEqualTo("franchise-9");
        assertThat(account.getBankName()).isEqualTo("jaybank");
        assertThat(account.getBankAccountNumber()).isEqualTo("123-456-789");
    }
}
