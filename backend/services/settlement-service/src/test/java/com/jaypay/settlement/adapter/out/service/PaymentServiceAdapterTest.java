package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.Payment;
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

class PaymentServiceAdapterTest {

    private static final String NORMAL_STATUS_PAYMENTS =
            "[{\"paymentId\":5,\"requestMembershipId\":\"7\",\"requestPrice\":1000,\"franchiseId\":\"franchise-9\","
                    + "\"franchiseFeeRate\":\"10\",\"paymentStatus\":0,\"approvedAt\":null}]";

    private final List<String> finishedSettlements = new ArrayList<>();
    private HttpServer paymentService;
    private PaymentServiceAdapter adapter;

    @BeforeEach
    void startPaymentService() throws IOException {
        paymentService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        paymentService.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String response = "{\"status\":404}";
            int status = 404;
            if (path.equals("/payment/normal-status")) {
                response = NORMAL_STATUS_PAYMENTS;
                status = 200;
            } else if (path.equals("/payment/finish-settlement") && exchange.getRequestMethod().equals("POST")) {
                finishedSettlements.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                response = "";
                status = 200;
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        paymentService.start();
        adapter = new PaymentServiceAdapter(
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP),
                "http://127.0.0.1:" + paymentService.getAddress().getPort());
    }

    @AfterEach
    void stopPaymentService() {
        paymentService.stop(0);
    }

    @Test
    void readsTheNormalStatusPaymentsFromThePaymentService() {
        assertThat(adapter.getNormalStatusPayments())
                .containsExactly(new Payment(5, "franchise-9", 1000, "10"));
    }

    @Test
    void finishesTheSettlementOfAPayment() {
        adapter.finishSettlement(5);

        assertThat(finishedSettlements).containsExactly("{\"paymentId\":\"5\"}");
    }
}
