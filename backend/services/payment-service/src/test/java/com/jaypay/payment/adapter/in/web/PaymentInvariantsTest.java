package com.jaypay.payment.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.jaypay.payment.application.port.in.ResumeUnconfirmedPaymentsUseCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"payment.fee-rate-basis-points=250", "payment.amount.max=10000000",
                "payment.sweep.every=1h", "payment.sweep.retry-after=0s", "payment.sweep.attempts=3"})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PaymentInvariantsTest {

    private static final int CONCURRENT_REQUESTS = 16;
    private static final AtomicLong MEMBERS = new AtomicLong(7000);
    private static final Set<String> CORPORATE = ConcurrentHashMap.newKeySet();
    private static final Set<String> INVALID = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> BALANCES = new ConcurrentHashMap<>();
    private static final Map<String, String> MONEY_RESULTS = new ConcurrentHashMap<>();
    private static final List<String> DECREASES = new CopyOnWriteArrayList<>();
    private static final Set<String> LOSE_NEXT_DEBIT_ANSWER = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAIL_NEXT_DEBIT = ConcurrentHashMap.newKeySet();
    private static final Set<String> MONEY_SILENT = ConcurrentHashMap.newKeySet();
    private static final HttpServer JAYPAY = startFakeJayPay();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private ResumeUnconfirmedPaymentsUseCase sweep;

    @Autowired
    private JdbcTemplate database;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + JAYPAY.getAddress().getPort();
        registry.add("service.membership.url", () -> url);
        registry.add("service.money.url", () -> url);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @AfterAll
    static void stopFakeJayPay() {
        JAYPAY.stop(0);
    }

    @Test
    void debitsThePayerExactlyTheAmountAndChargesTheServerFeeRate() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();

        JsonNode payment = pay(UUID.randomUUID().toString(), payer, merchant, "3000");

        assertThat(payment.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(payment.get("requestPrice").asLong()).isEqualTo(3000);
        assertThat(payment.get("feeRateBasisPoints").asInt()).isEqualTo(250);
        assertThat(payment.get("fee").asLong()).isEqualTo(75);
        assertThat(payment.get("payout").asLong()).isEqualTo(2925);
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
        assertThat(DECREASES).contains("payment-" + payment.get("paymentId").asLong() + "-decrease");
    }

    @Test
    void aPaymentAboveTheBalanceFailsKeepsTheBalanceAndIsRecordedAsFailed() throws Exception {
        String payer = memberWithBalance(1000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();

        JsonNode payment = pay(requestId, payer, merchant, "3000");

        assertThat(payment.get("paymentStatus").asText()).isEqualTo("FAILED");
        assertThat(BALANCES.get(payer)).isEqualTo(1000);
        assertThat(pay(requestId, payer, merchant, "3000")).isEqualTo(payment);
        assertThat(paidPaymentIds()).doesNotContain(payment.get("paymentId").asLong());
    }

    @Test
    void ignoresAFeeRateInTheBody() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();

        HttpResponse<String> response = post("/payment/request", "{\"requestId\":\"" + UUID.randomUUID()
                + "\",\"requestMembershipId\":\"" + payer + "\",\"franchiseId\":\"" + merchant
                + "\",\"requestPrice\":4000,\"franchiseFeeRate\":\"-50\",\"feeRateBasisPoints\":0,\"fee\":0}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode payment = JSON.readTree(response.body());
        assertThat(payment.get("feeRateBasisPoints").asInt()).isEqualTo(250);
        assertThat(payment.get("fee").asLong()).isEqualTo(100);
        assertThat(payment.get("payout").asLong()).isEqualTo(3900);
    }

    @Test
    void refusesAMerchantThatIsNotAValidCorporateMember() throws Exception {
        String payer = memberWithBalance(10000);
        String personal = String.valueOf(MEMBERS.incrementAndGet());
        String closedCorporate = corporateMember();
        INVALID.add(closedCorporate);

        for (String merchant : List.of(personal, closedCorporate, "not-a-number")) {
            HttpResponse<String> refused = request(UUID.randomUUID().toString(), payer, merchant, "1000");

            assertThat(refused.statusCode()).as(merchant).isEqualTo(400);
            assertThat(JSON.readTree(refused.body()).get("error").asText()).isEqualTo("INVALID_MERCHANT");
        }
        assertThat(BALANCES.get(payer)).isEqualTo(10000);
    }

    @Test
    void refusesAPayerThatIsNotAValidMember() throws Exception {
        String payer = memberWithBalance(10000);
        INVALID.add(payer);

        HttpResponse<String> refused = request(UUID.randomUUID().toString(), payer, corporateMember(), "1000");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(refused.body()).get("error").asText()).isEqualTo("INVALID_PAYER");
        assertThat(BALANCES.get(payer)).isEqualTo(10000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1000", "0", "10000001", "1.5", "\"ten\"", "null", "9223372036854775808"})
    void refusesAnAmountThatIsNotAPositiveWholeNumberWithinTheLimit(String amount) throws Exception {
        String payer = memberWithBalance(20_000_000);

        HttpResponse<String> refused = request(UUID.randomUUID().toString(), payer, corporateMember(), amount);

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(refused.body()).get("error").asText()).isIn("INVALID_AMOUNT", "INVALID_REQUEST");
        assertThat(BALANCES.get(payer)).isEqualTo(20_000_000);
    }

    @Test
    void acceptsTheLimitItself() throws Exception {
        String payer = memberWithBalance(10_000_000);

        JsonNode payment = pay(UUID.randomUUID().toString(), payer, corporateMember(), "10000000");

        assertThat(payment.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(payment.get("fee").asLong()).isEqualTo(250_000);
        assertThat(BALANCES.get(payer)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"requestId\":\"\",", "\"requestId\":\"has spaces\","})
    void refusesAPaymentWithoutAUsableRequestId(String requestId) throws Exception {
        String payer = memberWithBalance(5000);

        HttpResponse<String> refused = post("/payment/request", "{" + requestId + "\"requestMembershipId\":\"" + payer
                + "\",\"franchiseId\":\"" + corporateMember() + "\",\"requestPrice\":1000}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(refused.body()).get("error").asText()).isEqualTo("INVALID_REQUEST_ID");
        assertThat(BALANCES.get(payer)).isEqualTo(5000);
    }

    @Test
    void refusesFormFieldsSoAnotherSiteCannotSubmitAPayment() throws Exception {
        String payer = memberWithBalance(5000);

        HttpResponse<String> refused = HTTP.send(HttpRequest.newBuilder(uri("/payment/request"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("requestId=" + UUID.randomUUID() + "&requestMembershipId=" + payer
                        + "&franchiseId=" + corporateMember() + "&requestPrice=1000"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(refused.statusCode()).isEqualTo(415);
        assertThat(BALANCES.get(payer)).isEqualTo(5000);
    }

    @Test
    void aRepeatedPaymentDebitsOnceAndAnswersWithTheFirstResult() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();

        JsonNode first = pay(requestId, payer, merchant, "3000");
        JsonNode second = pay(requestId, payer, merchant, "3000");
        JsonNode third = pay(requestId, payer, merchant, "3000");

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
    }

    @RepeatedTest(3)
    void concurrentCopiesOfOnePaymentDebitOnce() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();

        List<JsonNode> results = concurrently(() -> pay(requestId, payer, merchant, "3000"));

        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo(results.getFirst()));
        assertThat(results.getFirst().get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payer becomes invalid", "merchant becomes invalid", "merchant loses corp"})
    void aPaymentWhoseDebitAnswerWasLostFinishesOnTheRepeatAfterThePartiesChange(String change) throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();
        LOSE_NEXT_DEBIT_ANSWER.add(payer);

        HttpResponse<String> lost = request(requestId, payer, merchant, "3000");
        switch (change) {
            case "payer becomes invalid" -> INVALID.add(payer);
            case "merchant becomes invalid" -> INVALID.add(merchant);
            default -> CORPORATE.remove(merchant);
        }
        JsonNode repeat = pay(requestId, payer, merchant, "3000");

        assertThat(lost.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(lost.body()).get("error").asText()).isEqualTo("PAYMENT_NOT_CONFIRMED");
        assertThat(repeat.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(pay(requestId, payer, merchant, "3000")).isEqualTo(repeat);
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
        assertThat(paidPaymentIds()).contains(repeat.get("paymentId").asLong());
    }

    @Test
    void aPaymentWhoseRefusedDebitAnswerWasLostFailsOnTheRepeatAndKeepsTheBalance() throws Exception {
        String payer = memberWithBalance(1000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();
        LOSE_NEXT_DEBIT_ANSWER.add(payer);

        assertThat(request(requestId, payer, merchant, "3000").statusCode()).isEqualTo(503);
        INVALID.add(payer);
        JsonNode repeat = pay(requestId, payer, merchant, "3000");

        assertThat(repeat.get("paymentStatus").asText()).isEqualTo("FAILED");
        assertThat(BALANCES.get(payer)).isEqualTo(1000);
        assertThat(paidPaymentIds()).doesNotContain(repeat.get("paymentId").asLong());
    }

    @Test
    void aDebitMoneyServiceFailsWith5xxTakesNothingAndTheRepeatDebitsOnce() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();
        FAIL_NEXT_DEBIT.add(payer);

        HttpResponse<String> failed = request(requestId, payer, merchant, "3000");

        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(failed.body()).get("error").asText()).isEqualTo("PAYMENT_NOT_CONFIRMED");
        assertThat(BALANCES.get(payer)).isEqualTo(10000);

        JsonNode repeat = pay(requestId, payer, merchant, "3000");

        assertThat(repeat.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(pay(requestId, payer, merchant, "3000")).isEqualTo(repeat);
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
        assertThat(paidPaymentIds()).contains(repeat.get("paymentId").asLong());
    }

    @Test
    void aPaymentWhoseDebitAnswerWasLostIsPaidByTheSweepWithoutARepeat() throws Exception {
        String payer = memberWithBalance(10000);
        String requestId = UUID.randomUUID().toString();
        LOSE_NEXT_DEBIT_ANSWER.add(payer);
        assertThat(request(requestId, payer, corporateMember(), "3000").statusCode()).isEqualTo(503);

        sweep.resumeUnconfirmedPayments();

        assertThat(storedStatus(requestId)).isEqualTo("PAID");
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
        assertThat(paidPaymentIds()).contains(storedPaymentId(requestId));
        assertThat(flowSteps(requestId, 2)).extracting(PaymentInvariantsTest::stepAndOutcome)
                .containsExactly("PAYMENT_REQUESTED SUCCEEDED", "PAYER_DEBITED SUCCEEDED");
    }

    @Test
    void aPaymentWhoseRefusedDebitAnswerWasLostIsFailedByTheSweepAndKeepsTheBalance() throws Exception {
        String payer = memberWithBalance(1000);
        String requestId = UUID.randomUUID().toString();
        LOSE_NEXT_DEBIT_ANSWER.add(payer);
        assertThat(request(requestId, payer, corporateMember(), "3000").statusCode()).isEqualTo(503);

        sweep.resumeUnconfirmedPayments();

        assertThat(storedStatus(requestId)).isEqualTo("FAILED");
        assertThat(BALANCES.get(payer)).isEqualTo(1000);
        assertThat(paidPaymentIds()).doesNotContain(storedPaymentId(requestId));
    }

    @Test
    void theSweepGivesUpAfterItsAttemptsWithAnErrorAndARepeatStillFinishesThePayment(CapturedOutput output) throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();
        MONEY_SILENT.add(payer);
        assertThat(request(requestId, payer, merchant, "3000").statusCode()).isEqualTo(503);
        String debitId = "payment-" + storedPaymentId(requestId) + "-decrease";

        for (int sweeps = 0; sweeps < 5; sweeps++) {
            sweep.resumeUnconfirmedPayments();
        }

        assertThat(DECREASES.stream().filter(debitId::equals)).hasSize(1 + 3);
        assertThat(storedStatus(requestId)).isEqualTo("REQUESTED");
        assertThat(output.getOut().lines().filter(line -> line.contains("ERROR") && line.contains(debitId))).hasSize(1);

        MONEY_SILENT.remove(payer);
        assertThat(pay(requestId, payer, merchant, "3000").get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
    }

    @RepeatedTest(5)
    void sweepsAndRepeatsOfALostPaymentAtOnceDebitItOnce() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();
        LOSE_NEXT_DEBIT_ANSWER.add(payer);
        assertThat(request(requestId, payer, merchant, "3000").statusCode()).isEqualTo(503);
        AtomicInteger turn = new AtomicInteger();

        List<JsonNode> results = concurrently(() -> {
            if (turn.getAndIncrement() % 2 == 0) {
                sweep.resumeUnconfirmedPayments();
                return null;
            }
            return pay(requestId, payer, merchant, "3000");
        });

        assertThat(results.stream().filter(result -> result != null))
                .hasSize(CONCURRENT_REQUESTS / 2)
                .allSatisfy(result -> assertThat(result.get("paymentStatus").asText()).isEqualTo("PAID"));
        assertThat(storedStatus(requestId)).isEqualTo("PAID");
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
    }

    @Test
    void refusesARequestIdReusedForADifferentPayment() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();
        pay(requestId, payer, merchant, "3000");

        HttpResponse<String> reused = request(requestId, payer, merchant, "4000");

        assertThat(reused.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(reused.body()).get("error").asText()).isEqualTo("REQUEST_ID_REUSED");
        assertThat(BALANCES.get(payer)).isEqualTo(7000);
    }

    @Test
    void listsOnlyPaidPaymentsForSettlement() throws Exception {
        String payer = memberWithBalance(5000);
        String merchant = corporateMember();
        long paid = pay(UUID.randomUUID().toString(), payer, merchant, "3000").get("paymentId").asLong();
        long failed = pay(UUID.randomUUID().toString(), payer, merchant, "3000").get("paymentId").asLong();
        long settled = pay(UUID.randomUUID().toString(), payer, merchant, "1000").get("paymentId").asLong();
        finishSettlement(settled);

        assertThat(paidPaymentIds()).contains(paid).doesNotContain(failed, settled);
    }

    @RepeatedTest(3)
    void concurrentSettlementsOfOnePaymentSettleItOnce() throws Exception {
        String payer = memberWithBalance(5000);
        long paymentId = pay(UUID.randomUUID().toString(), payer, corporateMember(), "3000").get("paymentId").asLong();

        List<JsonNode> results = concurrently(() -> finishSettlement(paymentId));

        assertThat(results).allSatisfy(result -> assertThat(result.get("paymentStatus").asText()).isEqualTo("SETTLED"));
        assertThat(results.stream().filter(result -> !result.get("alreadySettled").asBoolean())).hasSize(1);
        assertThat(finishSettlement(paymentId).get("alreadySettled").asBoolean()).isTrue();
        assertThat(paidPaymentIds()).doesNotContain(paymentId);
    }

    @Test
    void refusesToSettleAFailedOrUnknownPayment() throws Exception {
        String payer = memberWithBalance(0);
        long failed = pay(UUID.randomUUID().toString(), payer, corporateMember(), "3000").get("paymentId").asLong();

        HttpResponse<String> notPaid = post("/payment/finish-settlement", "{\"paymentId\":\"" + failed + "\"}");
        HttpResponse<String> unknown = post("/payment/finish-settlement", "{\"paymentId\":\"999999999\"}");

        assertThat(notPaid.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(notPaid.body()).get("error").asText()).isEqualTo("PAYMENT_NOT_PAID");
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(unknown.body()).get("error").asText()).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    void aPaidPaymentReportsItsRequestAndItsDebitToItsFlowUnderItsRequestId() throws Exception {
        String payer = memberWithBalance(10000);
        String merchant = corporateMember();
        String requestId = UUID.randomUUID().toString();

        pay(requestId, payer, merchant, "3000");
        pay(requestId, payer, merchant, "3000");

        List<JsonNode> steps = flowSteps(requestId, 2);
        assertThat(steps).extracting(PaymentInvariantsTest::stepAndOutcome)
                .containsExactly("PAYMENT_REQUESTED SUCCEEDED", "PAYER_DEBITED SUCCEEDED");
        assertThat(steps).allSatisfy(step -> {
            assertThat(step.get("flowType").asText()).isEqualTo("PAYMENT");
            assertThat(step.get("service").asText()).isEqualTo("payment-service");
            assertThat(step.get("membershipId").asText()).isEqualTo(payer);
            assertThat(step.get("amount").asLong()).isEqualTo(3000);
        });
    }

    @Test
    void aPaymentAboveTheBalanceReportsTheRefusedDebit() throws Exception {
        String payer = memberWithBalance(1000);
        String requestId = UUID.randomUUID().toString();

        pay(requestId, payer, corporateMember(), "3000");

        List<JsonNode> steps = flowSteps(requestId, 2);
        assertThat(steps).extracting(PaymentInvariantsTest::stepAndOutcome)
                .containsExactly("PAYMENT_REQUESTED SUCCEEDED", "PAYER_DEBITED FAILED");
        assertThat(steps.get(1).get("reason").asText()).isEqualTo("money-service refused to take the price from the payer's wallet");
    }

    @Test
    void aRefusedMerchantReportsTheRequestAsFailedWithTheReason() throws Exception {
        String payer = memberWithBalance(10000);
        String personal = String.valueOf(MEMBERS.incrementAndGet());
        String requestId = UUID.randomUUID().toString();

        assertThat(request(requestId, payer, personal, "1000").statusCode()).isEqualTo(400);

        JsonNode step = flowSteps(requestId, 1).getFirst();
        assertThat(stepAndOutcome(step)).isEqualTo("PAYMENT_REQUESTED FAILED");
        assertThat(step.get("reason").asText()).isEqualTo("The merchant does not exist, is not valid or is not a corporate member");
        assertThat(step.get("membershipId").asText()).isEqualTo(payer);
    }

    private String storedStatus(String requestId) {
        return database.queryForObject("SELECT payment_status FROM payment WHERE request_id = ?", String.class, requestId);
    }

    private long storedPaymentId(String requestId) {
        return database.queryForObject("SELECT payment_id FROM payment WHERE request_id = ?", Long.class, requestId);
    }

    private static List<JsonNode> flowSteps(String requestId, int count) {
        return KafkaTestBroker.awaitValues("jaypay.flow.events", requestId, values -> values.size() >= count, Duration.ofSeconds(30))
                .stream().map(PaymentInvariantsTest::readJson).toList();
    }

    private static JsonNode readJson(String value) {
        try {
            return JSON.readTree(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stepAndOutcome(JsonNode step) {
        return step.get("step").asText() + " " + step.get("outcome").asText();
    }

    private static String memberWithBalance(long balance) {
        String member = String.valueOf(MEMBERS.incrementAndGet());
        BALANCES.put(member, balance);
        return member;
    }

    private static String corporateMember() {
        String member = String.valueOf(MEMBERS.incrementAndGet());
        CORPORATE.add(member);
        return member;
    }

    private JsonNode pay(String requestId, String payer, String merchant, String amount) throws Exception {
        HttpResponse<String> response = request(requestId, payer, merchant, amount);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> request(String requestId, String payer, String merchant, String amount) throws Exception {
        return post("/payment/request", "{\"requestId\":\"" + requestId + "\",\"requestMembershipId\":\"" + payer
                + "\",\"franchiseId\":\"" + merchant + "\",\"requestPrice\":" + amount + "}");
    }

    private JsonNode finishSettlement(long paymentId) throws Exception {
        HttpResponse<String> response = post("/payment/finish-settlement", "{\"paymentId\":\"" + paymentId + "\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private List<Long> paidPaymentIds() throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri("/payment/normal-status")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        List<Long> ids = new ArrayList<>();
        for (JsonNode payment : JSON.readTree(response.body())) {
            assertThat(payment.get("paymentStatus").asText()).isEqualTo("PAID");
            ids.add(payment.get("paymentId").asLong());
        }
        return ids;
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static List<JsonNode> concurrently(Callable<JsonNode> request) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<JsonNode>> pending = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                pending.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> result : pending) {
                results.add(result.get());
            }
            return results;
        }
    }

    private static HttpServer startFakeJayPay() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(CONCURRENT_REQUESTS * 2));
            server.createContext("/memberships/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/memberships/".length());
                respond(exchange, 200, "{\"membershipId\":\"" + member + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                        + "\"address\":\"Seocho\",\"valid\":" + !INVALID.contains(member) + ",\"corp\":" + CORPORATE.contains(member) + "}");
            });
            server.createContext("/money/decrease", exchange -> {
                JsonNode change = JSON.readTree(exchange.getRequestBody().readAllBytes());
                String requestId = change.get("requestId").asText();
                String payer = change.get("targetMembershipId").asText();
                DECREASES.add(requestId);
                if (FAIL_NEXT_DEBIT.remove(payer) || MONEY_SILENT.contains(payer)) {
                    respond(exchange, 500, "{\"status\":500}");
                    return;
                }
                String answer = MONEY_RESULTS.computeIfAbsent(requestId, id -> decrease(id, payer, change.get("amount").asLong()));
                if (LOSE_NEXT_DEBIT_ANSWER.remove(payer)) {
                    respond(exchange, 503, "{}");
                } else {
                    respond(exchange, 200, answer);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String decrease(String requestId, String member, long amount) {
        long[] status = {2};
        BALANCES.computeIfPresent(member, (id, balance) -> {
            if (balance < amount) {
                return balance;
            }
            status[0] = 1;
            return balance - amount;
        });
        return "{\"moneyChangingRequestId\":\"" + requestId + "\",\"moneyChangingType\":2,\"moneyChangingResultStatus\":"
                + status[0] + ",\"amount\":" + amount + "}";
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
