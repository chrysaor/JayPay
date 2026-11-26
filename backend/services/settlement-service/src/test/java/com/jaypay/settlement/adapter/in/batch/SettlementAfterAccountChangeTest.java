package com.jaypay.settlement.adapter.in.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.jaypay.settlement.application.port.in.SettlementUseCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SettlementAfterAccountChangeTest {

    private static final int CONCURRENT_RUNS = 4;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Long, String> MERCHANT = Map.of(1L, "21", 2L, "22", 3L, "23");
    private static final Map<Long, Long> PAYOUT = Map.of(1L, 9700L, 2L, 4825L, 3L, 194L);
    private static final Set<Long> PAID = ConcurrentHashMap.newKeySet();
    private static final Set<Long> SETTLED = ConcurrentHashMap.newKeySet();
    private static final Set<Long> FINISH_ANSWER_LOST = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> ACCOUNTS = new ConcurrentHashMap<>();
    private static final Set<String> REFUSING_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SILENT_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> BOUND = new ConcurrentHashMap<>();
    private static final Map<String, String> ANSWERED = new ConcurrentHashMap<>();
    private static final List<String> SENT_TO_THE_BANK = new CopyOnWriteArrayList<>();
    private static final HttpServer JAYPAY = startFakeJayPay();

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job settlement;

    @Autowired
    private SettlementUseCase settlementUseCase;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + JAYPAY.getAddress().getPort();
        registry.add("service.payment.url", () -> url);
        registry.add("service.banking.url", () -> url);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @AfterAll
    static void stopFakeJayPay() {
        JAYPAY.stop(0);
    }

    @Test
    void aRefusedPayoutLandsOnTheNewAccountOnceAndAPayoutAlreadySentIsNeverSentToTheNewAccount() throws Exception {
        PAID.addAll(Set.of(1L, 2L, 3L));
        ACCOUNTS.putAll(Map.of("21", "100-21", "22", "100-22", "23", "100-23"));
        REFUSING_ACCOUNTS.add("100-21");
        FINISH_ANSWER_LOST.add(2L);
        SILENT_ACCOUNTS.add("100-23");

        assertThat(run()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(SETTLED).isEmpty();
        assertThat(SENT_TO_THE_BANK).containsExactlyInAnyOrder("100-21:9700 FAILED", "100-22:4825 SUCCEEDED");

        ACCOUNTS.putAll(Map.of("21", "200-21", "22", "200-22", "23", "200-23"));
        SILENT_ACCOUNTS.clear();
        runConcurrently();
        assertThat(run()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(run()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(SETTLED).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(SENT_TO_THE_BANK).containsExactlyInAnyOrder(
                "100-21:9700 FAILED", "200-21:9700 SUCCEEDED", "100-22:4825 SUCCEEDED", "100-23:194 SUCCEEDED");
    }

    private void runConcurrently() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_RUNS)) {
            List<Future<?>> runs = new CopyOnWriteArrayList<>();
            for (int i = 0; i < CONCURRENT_RUNS; i++) {
                runs.add(pool.submit(settlementUseCase::settleNormalStatusPayments));
            }
            for (Future<?> run : runs) {
                run.get();
            }
        }
    }

    private BatchStatus run() throws Exception {
        return jobOperator.start(settlement, new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .toJobParameters()).getStatus();
    }

    private static HttpServer startFakeJayPay() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(CONCURRENT_RUNS * 2));
            server.createContext("/payment/normal-status", exchange -> respond(exchange, 200, JSON.writeValueAsString(
                    PAID.stream().sorted().map(SettlementAfterAccountChangeTest::payment).toList())));
            server.createContext("/payment/finish-settlement", exchange -> {
                long paymentId = JSON.readTree(exchange.getRequestBody().readAllBytes()).get("paymentId").asLong();
                if (FINISH_ANSWER_LOST.remove(paymentId)) {
                    respond(exchange, 503, "{}");
                    return;
                }
                boolean settledNow = PAID.remove(paymentId);
                if (settledNow) {
                    SETTLED.add(paymentId);
                }
                respond(exchange, 200, "{\"paymentId\":" + paymentId + ",\"paymentStatus\":\"SETTLED\",\"alreadySettled\":" + !settledNow + "}");
            });
            server.createContext("/banking/account/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/banking/account/".length());
                respond(exchange, 200, "{\"membershipId\":\"" + member + "\",\"bankName\":\"jaybank\",\"bankAccountNumber\":\""
                        + ACCOUNTS.get(member) + "\",\"linkedStatusIsValid\":true}");
            });
            server.createContext("/banking/firmbanking/request", SettlementAfterAccountChangeTest::firmBanking);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static synchronized void firmBanking(HttpExchange exchange) throws IOException {
        JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
        String requestId = request.get("requestId").asText();
        String account = request.get("toBankAccountNumber").asText();
        String transfer = account + ":" + request.get("moneyAmount").asLong();
        if (!BOUND.computeIfAbsent(requestId, id -> transfer).equals(transfer)) {
            respond(exchange, 409, "{\"error\":\"REQUEST_ID_REUSED\",\"message\":\"reused\"}");
            return;
        }
        if (!ANSWERED.containsKey(requestId)) {
            if (SILENT_ACCOUNTS.contains(account)) {
                respond(exchange, 503, "{\"error\":\"TRANSFER_NOT_CONFIRMED\",\"message\":\"no answer\"}");
                return;
            }
            String status = REFUSING_ACCOUNTS.contains(account) ? "FAILED" : "SUCCEEDED";
            ANSWERED.put(requestId, status);
            SENT_TO_THE_BANK.add(transfer + " " + status);
        }
        respond(exchange, 200, "{\"requestId\":\"" + requestId + "\",\"transferStatus\":\"" + ANSWERED.get(requestId) + "\"}");
    }

    private static Map<String, Object> payment(long paymentId) {
        return Map.of("paymentId", paymentId, "franchiseId", MERCHANT.get(paymentId), "requestPrice", 10000L, "fee", 300L,
                "payout", PAYOUT.get(paymentId), "paymentStatus", "PAID");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
