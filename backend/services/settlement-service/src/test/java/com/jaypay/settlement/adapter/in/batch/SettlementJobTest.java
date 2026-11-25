package com.jaypay.settlement.adapter.in.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import com.jaypay.settlement.application.port.in.SettlementUseCase;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SettlementJobTest {

    private static final int CONCURRENT_RUNS = 4;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Long, Long> PAID = new ConcurrentHashMap<>(Map.of(1L, 9700L, 2L, 4825L, 3L, 194L));
    private static final Set<Long> SETTLED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> LANDED = new ConcurrentHashMap<>();
    private static final List<String> ASKED = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean BANK_SILENT = new AtomicBoolean(true);
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
    }

    @AfterAll
    static void stopFakeJayPay() {
        JAYPAY.stop(0);
    }

    @Test
    void concurrentAndRepeatedRunsPayEachPaymentOnceAfterTheBankAnswersAgain() throws Exception {
        assertThat(run()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(SETTLED).isEmpty();
        assertThat(ASKED).isNotEmpty();
        BANK_SILENT.set(false);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_RUNS)) {
            List<Future<?>> runs = new CopyOnWriteArrayList<>();
            for (int i = 0; i < CONCURRENT_RUNS; i++) {
                runs.add(pool.submit(settlementUseCase::settleNormalStatusPayments));
            }
            for (Future<?> run : runs) {
                run.get();
            }
        }
        assertThat(run()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(run()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(SETTLED).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(LANDED).containsExactlyInAnyOrderEntriesOf(Map.of(
                "settlement-1", "100-1:9700", "settlement-2", "100-2:4825", "settlement-3", "100-1:194"));
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
                    List.of(payment(1, "11"), payment(2, "12"), payment(3, "11")).stream()
                            .filter(payment -> PAID.containsKey((Long) payment.get("paymentId")))
                            .toList())));
            server.createContext("/payment/finish-settlement", exchange -> {
                long paymentId = JSON.readTree(exchange.getRequestBody().readAllBytes()).get("paymentId").asLong();
                boolean settledNow = PAID.remove(paymentId) != null;
                if (settledNow) {
                    SETTLED.add(paymentId);
                }
                respond(exchange, 200, "{\"paymentId\":" + paymentId + ",\"paymentStatus\":\"SETTLED\",\"alreadySettled\":" + !settledNow + "}");
            });
            server.createContext("/banking/account/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/banking/account/".length());
                respond(exchange, 200, "{\"membershipId\":\"" + member + "\",\"bankName\":\"jaybank\",\"bankAccountNumber\":\"100-"
                        + member.substring(1) + "\",\"linkedStatusIsValid\":true}");
            });
            server.createContext("/banking/firmbanking/request", exchange -> {
                var transfer = JSON.readTree(exchange.getRequestBody().readAllBytes());
                String requestId = transfer.get("requestId").asText();
                ASKED.add(requestId);
                LANDED.putIfAbsent(requestId, transfer.get("toBankAccountNumber").asText() + ":" + transfer.get("moneyAmount").asLong());
                if (BANK_SILENT.get()) {
                    respond(exchange, 503, "{\"error\":\"TRANSFER_NOT_CONFIRMED\",\"message\":\"no answer\"}");
                    return;
                }
                respond(exchange, 200, "{\"requestId\":\"" + requestId + "\",\"transferStatus\":\"SUCCEEDED\"}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> payment(long paymentId, String merchant) {
        return Map.of("paymentId", paymentId, "franchiseId", merchant, "requestPrice", 10000L, "fee", 300L,
                "payout", Map.of(1L, 9700L, 2L, 4825L, 3L, 194L).get(paymentId), "paymentStatus", "PAID");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
