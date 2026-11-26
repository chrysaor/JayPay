package com.jaypay.settlement.adapter.in.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SettlementWhenAServiceFailsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long PAYOUT = 9700;
    private static final AtomicLong PAYMENTS = new AtomicLong(300);
    private static final Set<Long> PAID = ConcurrentHashMap.newKeySet();
    private static final Set<Long> SETTLED = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean PAYMENT_LIST_FAILING = new AtomicBoolean();
    private static final Set<String> ACCOUNT_LOOKUP_FAILING = ConcurrentHashMap.newKeySet();
    private static final Set<String> PAYOUT_FAILING = ConcurrentHashMap.newKeySet();
    private static final Set<Long> FINISH_ANSWER_DROPPED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> LANDED = new ConcurrentHashMap<>();
    private static final List<String> SENT_TO_THE_BANK = new CopyOnWriteArrayList<>();
    private static final HttpServer JAYPAY = startFakeJayPay();

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job settlement;

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
    void aRunWhosePaymentListFailsWith5xxPaysNothingAndTheNextRunPaysOnce() throws Exception {
        long payment = paidPayment();
        PAYMENT_LIST_FAILING.set(true);

        run();

        assertThat(PAID).contains(payment);
        assertThat(sentFor(payment)).isEmpty();

        PAYMENT_LIST_FAILING.set(false);
        run();
        run();

        assertThat(SETTLED).contains(payment);
        assertThat(sentFor(payment)).containsExactly(account(payment) + ":" + PAYOUT);
    }

    @Test
    void aPaymentWhoseAccountLookupFailsWith5xxStaysPaidAndTheNextRunPaysOnce() throws Exception {
        long payment = paidPayment();
        ACCOUNT_LOOKUP_FAILING.add(merchant(payment));

        run();

        assertThat(PAID).contains(payment);
        assertThat(sentFor(payment)).isEmpty();

        ACCOUNT_LOOKUP_FAILING.remove(merchant(payment));
        run();
        run();

        assertThat(SETTLED).contains(payment);
        assertThat(sentFor(payment)).containsExactly(account(payment) + ":" + PAYOUT);
    }

    @Test
    void aPayoutBankingServiceFailsWith5xxSendsNothingStaysPaidAndTheNextRunPaysOnce() throws Exception {
        long payment = paidPayment();
        PAYOUT_FAILING.add(account(payment));

        run();

        assertThat(PAID).contains(payment);
        assertThat(sentFor(payment)).isEmpty();

        PAYOUT_FAILING.remove(account(payment));
        run();
        run();

        assertThat(SETTLED).contains(payment);
        assertThat(sentFor(payment)).containsExactly(account(payment) + ":" + PAYOUT);
    }

    @Test
    void aSettlementWhoseFinishAnswerWasLostIsNeitherPaidNorSettledAgain() throws Exception {
        long payment = paidPayment();
        FINISH_ANSWER_DROPPED.add(payment);

        run();

        assertThat(SETTLED).contains(payment);
        assertThat(PAID).doesNotContain(payment);

        run();
        run();

        assertThat(sentFor(payment)).containsExactly(account(payment) + ":" + PAYOUT);
        assertThat(LANDED).containsEntry("settlement-" + payment, account(payment) + ":" + PAYOUT);
    }

    private static long paidPayment() {
        long payment = PAYMENTS.incrementAndGet();
        PAID.add(payment);
        return payment;
    }

    private static String merchant(long payment) {
        return "m" + payment;
    }

    private static String account(long payment) {
        return "300-" + payment;
    }

    private static List<String> sentFor(long payment) {
        return SENT_TO_THE_BANK.stream().filter(sent -> sent.startsWith(account(payment) + ":")).toList();
    }

    private void run() throws Exception {
        jobOperator.start(settlement, new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .toJobParameters());
    }

    private static HttpServer startFakeJayPay() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/payment/normal-status", exchange -> {
                if (PAYMENT_LIST_FAILING.get()) {
                    respond(exchange, 500, "{\"status\":500}");
                    return;
                }
                respond(exchange, 200, JSON.writeValueAsString(PAID.stream().sorted().map(SettlementWhenAServiceFailsTest::payment).toList()));
            });
            server.createContext("/payment/finish-settlement", exchange -> {
                long paymentId = JSON.readTree(exchange.getRequestBody().readAllBytes()).get("paymentId").asLong();
                boolean settledNow = PAID.remove(paymentId);
                if (settledNow) {
                    SETTLED.add(paymentId);
                }
                if (FINISH_ANSWER_DROPPED.remove(paymentId)) {
                    exchange.close();
                    return;
                }
                respond(exchange, 200, "{\"paymentId\":" + paymentId + ",\"paymentStatus\":\"SETTLED\",\"alreadySettled\":" + !settledNow + "}");
            });
            server.createContext("/banking/account/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/banking/account/".length());
                if (ACCOUNT_LOOKUP_FAILING.contains(member)) {
                    respond(exchange, 500, "{\"status\":500}");
                    return;
                }
                respond(exchange, 200, "{\"membershipId\":\"" + member + "\",\"bankName\":\"jaybank\",\"bankAccountNumber\":\"300-"
                        + member.substring(1) + "\",\"linkedStatusIsValid\":true}");
            });
            server.createContext("/banking/firmbanking/request", SettlementWhenAServiceFailsTest::firmBanking);
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
        if (PAYOUT_FAILING.contains(account)) {
            respond(exchange, 500, "{\"status\":500}");
            return;
        }
        String transfer = account + ":" + request.get("moneyAmount").asLong();
        if (LANDED.putIfAbsent(requestId, transfer) == null) {
            SENT_TO_THE_BANK.add(transfer);
        }
        respond(exchange, 200, "{\"requestId\":\"" + requestId + "\",\"transferStatus\":\"SUCCEEDED\"}");
    }

    private static Map<String, Object> payment(long paymentId) {
        return Map.of("paymentId", paymentId, "franchiseId", merchant(paymentId), "requestPrice", 10000L, "fee", 300L,
                "payout", PAYOUT, "paymentStatus", "PAID");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
