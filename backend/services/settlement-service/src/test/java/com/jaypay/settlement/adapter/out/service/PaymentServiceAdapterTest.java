package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.Payment;
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

class PaymentServiceAdapterTest {

    private static final String NORMAL_STATUS_PAYMENTS =
            "[{\"paymentId\":5,\"requestId\":\"r-5\",\"requestMembershipId\":\"7\",\"franchiseId\":\"9\","
                    + "\"requestPrice\":10000,\"feeRateBasisPoints\":300,\"fee\":300,\"payout\":9700,"
                    + "\"paymentStatus\":\"PAID\",\"approvedAt\":\"2026-11-26T01:00:00Z\"}]";

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
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (request.contains("\"5\"")) {
                    response = "{\"paymentId\":5,\"paymentStatus\":\"SETTLED\",\"alreadySettled\":" + !finishedSettlements.isEmpty() + "}";
                    finishedSettlements.add(request);
                    status = 200;
                } else {
                    response = "{\"error\":\"PAYMENT_NOT_PAID\",\"message\":\"Only a paid payment can be settled.\"}";
                    status = 409;
                }
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
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                "http://127.0.0.1:" + paymentService.getAddress().getPort());
    }

    @AfterEach
    void stopPaymentService() {
        paymentService.stop(0);
    }

    @Test
    void readsThePaidPaymentsFromThePaymentService() {
        assertThat(adapter.getPaidPayments())
                .containsExactly(new Payment(5, "9", 9700));
    }

    @Test
    void settlesAPaymentAndTellsWhetherThisCallSettledIt() {
        assertThat(adapter.finishSettlement(5)).isTrue();
        assertThat(adapter.finishSettlement(5)).isFalse();

        assertThat(finishedSettlements).containsExactly("{\"paymentId\":\"5\"}", "{\"paymentId\":\"5\"}");
    }

    @Test
    void doesNotTreatAnErrorAnswerAsASettlement() {
        assertThat(adapter.finishSettlement(6)).isFalse();
    }
}
