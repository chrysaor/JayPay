package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.BankAccount;
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

class BankingServiceAdapterTest {

    private static final String BANKING_SERVICE_RESPONSE =
            "{\"registeredBankAccountId\":\"3\",\"membershipId\":\"franchise-9\",\"bankName\":\"jaybank\","
                    + "\"bankAccountNumber\":\"123-456-789\",\"linkedStatusIsValid\":true,\"aggregateIdentifier\":\"agg-1\"}";

    private final List<String> firmBankingRequests = new ArrayList<>();
    private HttpServer bankingService;
    private BankingServiceAdapter adapter;

    @BeforeEach
    void startBankingService() throws IOException {
        bankingService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bankingService.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String response = "{\"status\":404}";
            int status = 404;
            if (path.equals("/banking/account/franchise-9")) {
                response = BANKING_SERVICE_RESPONSE;
                status = 200;
            } else if (path.equals("/banking/firmbanking/request") && exchange.getRequestMethod().equals("POST")) {
                firmBankingRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                response = "{}";
                status = 200;
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
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
        BankAccount account = adapter.getRegisteredBankAccount("franchise-9");

        assertThat(account).isEqualTo(new BankAccount("jaybank", "123-456-789"));
    }

    @Test
    void sendsFirmBankingFromTheSettlementAccountToTheFranchiseAccount() {
        adapter.requestFirmBanking(new BankAccount("jaybank", "123-456-789"), 9000);

        assertThat(firmBankingRequests).containsExactly(
                "{\"fromBankName\":\"jaypay\",\"fromBankAccountNumber\":\"111-222-333\","
                        + "\"toBankName\":\"jaybank\",\"toBankAccountNumber\":\"123-456-789\",\"moneyAmount\":9000}");
    }
}
