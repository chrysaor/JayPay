package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.Payout;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BankingServiceAdapterTest {

    private static final String BANKING_SERVICE_RESPONSE =
            "{\"registeredBankAccountId\":\"3\",\"membershipId\":\"9\",\"bankName\":\"jaybank\","
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
            if (path.equals("/banking/account/9")) {
                response = BANKING_SERVICE_RESPONSE;
                status = 200;
            } else if (path.equals("/banking/firmbanking/request") && exchange.getRequestMethod().equals("POST")) {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                firmBankingRequests.add(request);
                String requestId = request.replaceAll(".*\"requestId\":\"([^\"]+)\".*", "$1");
                if (request.contains("503-503")) {
                    response = "{\"error\":\"TRANSFER_NOT_CONFIRMED\",\"message\":\"no answer\"}";
                    status = 503;
                } else if (request.contains("409-409")) {
                    response = "{\"error\":\"REQUEST_ID_REUSED\",\"message\":\"reused\"}";
                    status = 409;
                } else {
                    response = "{\"requestId\":\"" + requestId + "\",\"transferStatus\":\"" + (request.contains("404-404") ? "FAILED" : "SUCCEEDED") + "\"}";
                    status = 200;
                }
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        bankingService.start();
        adapter = new BankingServiceAdapter(
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                "http://127.0.0.1:" + bankingService.getAddress().getPort());
    }

    @AfterEach
    void stopBankingService() {
        bankingService.stop(0);
    }

    @Test
    void readsTheRegisteredAccountFromTheBankingService() {
        assertThat(adapter.getRegisteredBankAccount("9")).contains(new BankAccount("jaybank", "123-456-789"));
    }

    @Test
    void findsNoAccountForAMemberWithoutOne() {
        assertThat(adapter.getRegisteredBankAccount("10")).isEmpty();
    }

    @Test
    void sendsThePayoutFromTheSettlementAccountToTheMerchantUnderItsRequestId() {
        assertThat(adapter.pay("settlement-7", new BankAccount("jaybank", "123-456-789"), 9000)).isEqualTo(Payout.PAID);

        assertThat(firmBankingRequests).containsExactly(
                "{\"requestId\":\"settlement-7\",\"fromBankName\":\"jaypay\",\"fromBankAccountNumber\":\"111-222-333\","
                        + "\"toBankName\":\"jaybank\",\"toBankAccountNumber\":\"123-456-789\",\"moneyAmount\":9000}");
    }

    @Test
    void reportsAPayoutTheBankRefused() {
        assertThat(adapter.pay("settlement-8", new BankAccount("jaybank", "404-404"), 9000)).isEqualTo(Payout.REFUSED);
    }

    @Test
    void reportsAPayoutWithoutAConfirmationAsUnconfirmed() {
        assertThat(adapter.pay("settlement-9", new BankAccount("jaybank", "503-503"), 9000)).isEqualTo(Payout.UNCONFIRMED);
        assertThat(adapter.pay("settlement-10", new BankAccount("jaybank", "409-409"), 9000)).isEqualTo(Payout.UNCONFIRMED);
    }
}
