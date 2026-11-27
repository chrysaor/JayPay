package com.jaypay.remittance.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.jaypay.remittance.application.port.in.ResumeUnfinishedRemittancesUseCase;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"remittance.amount.max=10000000",
        "remittance.sweep.every=1h", "remittance.sweep.retry-after=0s", "remittance.sweep.attempts=3"})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RemittanceInvariantsTest {

    private static final int CONCURRENT_REQUESTS = 16;
    private static final AtomicLong MEMBERS = new AtomicLong(9000);
    private static final Set<String> INVALID = ConcurrentHashMap.newKeySet();
    private static final Set<String> UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final Set<String> CREDIT_REFUSED = ConcurrentHashMap.newKeySet();
    private static final Set<String> MONEY_SILENT = ConcurrentHashMap.newKeySet();
    private static final Set<String> REFUND_UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> BALANCES = new ConcurrentHashMap<>();
    private static final Map<String, String> MONEY_RESULTS = new ConcurrentHashMap<>();
    private static final Map<String, JsonNode> SUCCEEDED_DECREASES = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> UNANSWERED = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> FAILING = new ConcurrentHashMap<>();
    private static final List<String> MONEY_CALLS = new CopyOnWriteArrayList<>();
    private static final Set<String> INVALID_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REFUSED_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SILENT_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILING_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> TRANSFER_RESULTS = new ConcurrentHashMap<>();
    private static final List<JsonNode> ASKED = new CopyOnWriteArrayList<>();
    private static final Map<String, JsonNode> LANDED = new ConcurrentHashMap<>();
    private static final HttpServer JAYPAY = startFakeJayPay();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private ResumeUnfinishedRemittancesUseCase sweep;

    @Autowired
    private JdbcTemplate database;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + JAYPAY.getAddress().getPort();
        registry.add("service.membership.url", () -> url);
        registry.add("service.money.url", () -> url);
        registry.add("service.banking.url", () -> url);
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @AfterAll
    static void stopFakeJayPay() {
        JAYPAY.stop(0);
    }

    @Test
    void listsARemittanceForItsSenderAndItsReceiverAndForNobodyElse() throws Exception {
        String sender = memberWithBalance(20000);
        String receiver = memberWithBalance(0);
        String id = remit(toMember(UUID.randomUUID().toString(), sender, receiver, "5000")).get("remittanceId").asText();

        assertThat(history(sender)).extracting(remittance -> remittance.get("remittanceId").asText()).containsExactly(id);
        assertThat(history(receiver)).extracting(remittance -> remittance.get("remittanceId").asText()).containsExactly(id);
        assertThat(history(memberWithBalance(0))).isEmpty();
    }

    @Test
    void movesTheSameAmountOutOfTheSenderAndIntoTheReceiver() throws Exception {
        String sender = memberWithBalance(20000);
        String receiver = memberWithBalance(1000);

        JsonNode remittance = remit(toMember(UUID.randomUUID().toString(), sender, receiver, "5000"));

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remittance.get("fromMembershipId").asText()).isEqualTo(sender);
        assertThat(remittance.get("toMembershipId").asText()).isEqualTo(receiver);
        assertThat(remittance.get("remittanceType").asInt()).isZero();
        assertThat(remittance.get("amount").asLong()).isEqualTo(5000);
        assertThat(BALANCES.get(sender)).isEqualTo(15000);
        assertThat(BALANCES.get(receiver)).isEqualTo(6000);
        String id = remittance.get("remittanceId").asText();
        assertThat(MONEY_CALLS).contains("remittance-" + id + "-decrease", "remittance-" + id + "-increase");
    }

    @Test
    void aRemittanceAboveTheBalanceFailsAndKeepsBothBalances() throws Exception {
        String sender = memberWithBalance(3000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "5000");

        JsonNode remittance = remit(body);

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("FAILED");
        assertThat(BALANCES.get(sender)).isEqualTo(3000);
        assertThat(BALANCES.get(receiver)).isZero();
        assertThat(MONEY_CALLS).doesNotContain("remittance-" + remittance.get("remittanceId").asText() + "-increase");
        assertThat(remit(body)).isEqualTo(remittance);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1000", "0", "10000001", "1.5", "\"ten\"", "null", "9223372036854775808"})
    void refusesAnAmountThatIsNotAPositiveWholeNumberWithinTheLimit(String amount) throws Exception {
        String sender = memberWithBalance(20_000_000);
        String receiver = memberWithBalance(0);

        HttpResponse<String> refused = post(toMember(UUID.randomUUID().toString(), sender, receiver, amount));

        assertThat(refused.statusCode()).as(refused.body()).isEqualTo(400);
        assertThat(error(refused)).isIn("INVALID_AMOUNT", "INVALID_REQUEST");
        assertThat(BALANCES.get(sender)).isEqualTo(20_000_000);
        assertThat(BALANCES.get(receiver)).isZero();
    }

    @Test
    void acceptsTheLimitItself() throws Exception {
        String sender = memberWithBalance(10_000_000);
        String receiver = memberWithBalance(0);

        JsonNode remittance = remit(toMember(UUID.randomUUID().toString(), sender, receiver, "10000000"));

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isZero();
        assertThat(BALANCES.get(receiver)).isEqualTo(10_000_000);
    }

    @Test
    void refusesARemittanceToTheSenderItself() throws Exception {
        String sender = memberWithBalance(5000);
        int calls = MONEY_CALLS.size();

        HttpResponse<String> refused = post(toMember(UUID.randomUUID().toString(), sender, sender, "1000"));

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(error(refused)).isEqualTo("INVALID_RECEIVER");
        assertThat(BALANCES.get(sender)).isEqualTo(5000);
        assertThat(MONEY_CALLS).hasSize(calls);
    }

    @Test
    void refusesAReceiverThatIsNotAValidMember() throws Exception {
        String sender = memberWithBalance(5000);
        String closed = memberWithBalance(0);
        INVALID.add(closed);
        String missing = String.valueOf(MEMBERS.incrementAndGet());
        UNKNOWN.add(missing);

        for (String receiver : List.of(closed, missing, "not-a-number")) {
            HttpResponse<String> refused = post(toMember(UUID.randomUUID().toString(), sender, receiver, "1000"));

            assertThat(refused.statusCode()).as(receiver).isEqualTo(400);
            assertThat(error(refused)).isEqualTo("INVALID_RECEIVER");
        }
        assertThat(BALANCES.get(sender)).isEqualTo(5000);
        assertThat(BALANCES.get(closed)).isZero();
    }

    @Test
    void refusesASenderThatIsNotAValidMember() throws Exception {
        String sender = memberWithBalance(5000);
        INVALID.add(sender);

        HttpResponse<String> refused = post(toMember(UUID.randomUUID().toString(), sender, memberWithBalance(0), "1000"));

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(error(refused)).isEqualTo("INVALID_SENDER");
        assertThat(BALANCES.get(sender)).isEqualTo(5000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"requestId\":\"\",", "\"requestId\":\"has spaces\","})
    void refusesARemittanceWithoutAUsableRequestId(String requestId) throws Exception {
        String sender = memberWithBalance(5000);

        HttpResponse<String> refused = post("{" + requestId + "\"fromMembershipId\":\"" + sender + "\",\"toMembershipId\":\""
                + memberWithBalance(0) + "\",\"remittanceType\":0,\"amount\":1000}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(error(refused)).isEqualTo("INVALID_REQUEST_ID");
        assertThat(BALANCES.get(sender)).isEqualTo(5000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "-1", "null", "\"bank\""})
    void refusesAnUnknownRemittanceType(String type) throws Exception {
        String sender = memberWithBalance(5000);

        HttpResponse<String> refused = post("{\"requestId\":\"" + UUID.randomUUID() + "\",\"fromMembershipId\":\"" + sender
                + "\",\"toMembershipId\":\"" + memberWithBalance(0) + "\",\"remittanceType\":" + type + ",\"amount\":1000}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(error(refused)).isIn("INVALID_REMITTANCE_TYPE", "INVALID_REQUEST");
        assertThat(BALANCES.get(sender)).isEqualTo(5000);
    }

    @Test
    void refusesFormFieldsSoAnotherSiteCannotSubmitARemittance() throws Exception {
        String sender = memberWithBalance(5000);

        HttpResponse<String> refused = HTTP.send(HttpRequest.newBuilder(uri("/remittance/request"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("requestId=" + UUID.randomUUID() + "&fromMembershipId=" + sender
                        + "&toMembershipId=" + memberWithBalance(0) + "&remittanceType=0&amount=1000"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(refused.statusCode()).isEqualTo(415);
        assertThat(BALANCES.get(sender)).isEqualTo(5000);
    }

    @Test
    void aRepeatedRemittanceMovesTheMoneyOnceAndAnswersWithTheFirstResult() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "3000");

        JsonNode first = remit(body);
        JsonNode second = remit(body);
        JsonNode third = remit(body);

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        assertThat(BALANCES.get(sender)).isEqualTo(7000);
        assertThat(BALANCES.get(receiver)).isEqualTo(3000);
    }

    @RepeatedTest(3)
    void concurrentCopiesOfOneRemittanceMoveTheMoneyOnce() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "3000");

        List<JsonNode> results = concurrently(() -> remit(body));

        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo(results.getFirst()));
        assertThat(results.getFirst().get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7000);
        assertThat(BALANCES.get(receiver)).isEqualTo(3000);
    }

    @Test
    void refusesARequestIdReusedForADifferentRemittance() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();
        remit(toMember(requestId, sender, receiver, "3000"));

        for (String reused : List.of(toMember(requestId, sender, receiver, "4000"), toMember(requestId, sender, memberWithBalance(0), "3000"),
                toBank(requestId, sender, "Jay Bank", "1002-" + sender, "3000"))) {
            HttpResponse<String> refused = post(reused);

            assertThat(refused.statusCode()).as(reused).isEqualTo(409);
            assertThat(error(refused)).isEqualTo("REQUEST_ID_REUSED");
        }
        assertThat(BALANCES.get(sender)).isEqualTo(7000);
        assertThat(BALANCES.get(receiver)).isEqualTo(3000);
    }

    @Test
    void aCreditTheWalletRefusesIsRefundedToTheSender() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(500);
        CREDIT_REFUSED.add(receiver);

        JsonNode remittance = remit(toMember(UUID.randomUUID().toString(), sender, receiver, "4000"));

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("REFUNDED");
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isEqualTo(500);
        assertThat(MONEY_CALLS).contains("remittance-" + remittance.get("remittanceId").asText() + "-refund");
    }

    @Test
    void aDebitWithoutAnAnswerIsFinishedByTheSameRequestAgain() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-decrease", 1);

        HttpResponse<String> unconfirmed = post(body);

        assertThat(unconfirmed.statusCode()).as(unconfirmed.body()).isEqualTo(503);
        assertThat(error(unconfirmed)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isZero();

        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aCreditWithoutAnAnswerIsFinishedByTheSameRequestAgain() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-increase", 2);

        assertThat(post(body).statusCode()).isEqualTo(503);
        assertThat(post(body).statusCode()).isEqualTo(503);
        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aRefundWithoutAnAnswerIsFinishedByTheSameRequestAgain() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        CREDIT_REFUSED.add(receiver);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-refund", 1);

        HttpResponse<String> unconfirmed = post(body);

        assertThat(unconfirmed.statusCode()).isEqualTo(503);
        assertThat(error(unconfirmed)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        JsonNode finished = remit(body);
        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("REFUNDED");
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isZero();
    }

    @Test
    void aDebitMoneyServiceFailsWith5xxTakesNothingAndTheRepeatMovesTheMoneyOnce() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        failing("-decrease", 1);

        HttpResponse<String> failed = post(body);

        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(error(failed)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isZero();

        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aDebitWhoseAnswerWasLostCompletesOnTheRepeatAfterTheSenderSpentTheRest() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-decrease", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        BALANCES.put(sender, 0L);
        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isZero();
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aDebitWhoseAnswerWasLostCompletesOnTheRepeatAfterTheSenderBecomesInvalid() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-decrease", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        INVALID.add(sender);
        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aDebitWhoseAnswerWasLostIsRefundedOnTheRepeatAfterTheReceiverBecomesInvalid(boolean senderToo) throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-decrease", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        INVALID.add(receiver);
        if (senderToo) {
            INVALID.add(sender);
        }
        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("REFUNDED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isZero();
    }

    @Test
    void aCreditMoneyServiceFailsWith5xxIsFinishedByTheSameRequestAgain() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        failing("-increase", 1);

        HttpResponse<String> failed = post(body);

        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(error(failed)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isZero();

        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aCreditWhoseAnswerWasLostCompletesOnTheRepeatAfterTheReceiverBecomesInvalid() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-increase", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        INVALID.add(receiver);
        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aRefundMoneyServiceFailsWith5xxIsFinishedByTheSameRequestAgain() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        CREDIT_REFUSED.add(receiver);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        failing("-refund", 1);

        HttpResponse<String> failed = post(body);

        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(error(failed)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);

        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("REFUNDED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isZero();
    }

    @Test
    void aRefundMoneyServiceFailedWith5xxIsStillRefundedAfterTheSenderBecomesInvalid() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        CREDIT_REFUSED.add(receiver);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        failing("-refund", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        INVALID.add(sender);
        HttpResponse<String> repeated = post(body);
        INVALID.remove(sender);
        HttpResponse<String> afterValidAgain = post(body);

        assertThat(repeated.statusCode()).as(repeated.body()).isEqualTo(200);
        assertThat(afterValidAgain.statusCode()).as(afterValidAgain.body()).isEqualTo(200);
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
    }

    @Test
    void aRefundWhoseAnswerWasLostIsRefundedOnceOnTheRepeatAfterTheSenderBecomesInvalid() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        CREDIT_REFUSED.add(receiver);
        String body = toMember(UUID.randomUUID().toString(), sender, receiver, "2500");
        unanswered("-refund", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        INVALID.add(sender);
        JsonNode finished = remit(body);

        assertThat(finished.get("remittanceStatus").asText()).isEqualTo("REFUNDED");
        assertThat(remit(body)).isEqualTo(finished);
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isZero();
    }

    @Test
    void aBankRemittanceDebitsTheSenderAndSendsOneTransfer() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;

        JsonNode remittance = remit(toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000"));

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remittance.get("remittanceType").asInt()).isEqualTo(1);
        assertThat(remittance.get("toBankName").asText()).isEqualTo("Jay Bank");
        assertThat(remittance.get("toBankAccountNumber").asText()).isEqualTo(account);
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).singleElement().satisfies(transfer -> {
            assertThat(transfer.get("toBankName").asText()).isEqualTo("Jay Bank");
            assertThat(transfer.get("moneyAmount").asLong()).isEqualTo(7000);
        });
    }

    @Test
    void aBankRemittanceLeavesTheSourceAccountToTheBankingService() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1003-" + sender;

        remit(toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000"));

        assertThat(transfersTo(account)).singleElement()
                .satisfies(transfer -> assertThat(transfer.has("fromBankName") || transfer.has("fromBankAccountNumber")).isFalse());
    }

    @Test
    void aBankRemittanceWithoutEnoughMoneySendsNoTransfer() throws Exception {
        String sender = memberWithBalance(1000);
        String account = "1002-" + sender;

        JsonNode remittance = remit(toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000"));

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("FAILED");
        assertThat(BALANCES.get(sender)).isEqualTo(1000);
        assertThat(transfersTo(account)).isEmpty();
    }

    @Test
    void aTransferTheBankRefusesIsRefundedToTheSender() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        REFUSED_ACCOUNTS.add(account);
        String body = toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000");

        JsonNode remittance = remit(body);

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("REFUNDED");
        assertThat(BALANCES.get(sender)).isEqualTo(20000);
        assertThat(remit(body)).isEqualTo(remittance);
        assertThat(transfersTo(account)).isEmpty();
        assertThat(ASKED.stream().filter(transfer -> transfer.get("toBankAccountNumber").asText().equals(account))).hasSize(1);
    }

    @Test
    void aTransferTheBankDoesNotAnswerIsAskedAgainUnderTheSameIdAndLandsOnce() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        SILENT_ACCOUNTS.add(account);
        String body = toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000");

        HttpResponse<String> unanswered = post(body);
        assertThat(unanswered.statusCode()).isEqualTo(503);
        assertThat(error(unanswered)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        assertThat(post(body).statusCode()).isEqualTo(503);
        SILENT_ACCOUNTS.remove(account);
        JsonNode retried = remit(body);

        assertThat(retried.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(retried);
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).singleElement()
                .satisfies(transfer -> assertThat(transfer.get("requestId").asText()).isEqualTo("remittance-" + retried.get("remittanceId").asText() + "-transfer"));
        assertThat(ASKED.stream().filter(transfer -> transfer.get("toBankAccountNumber").asText().equals(account)).map(transfer -> transfer.get("requestId").asText()).distinct())
                .hasSize(1);
    }

    @Test
    void aTransferBankingServiceFailsWith5xxSendsNothingAndTheRepeatSendsItOnce() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        FAILING_ACCOUNTS.add(account);
        String body = toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000");

        HttpResponse<String> failed = post(body);

        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(error(failed)).isEqualTo("REMITTANCE_NOT_CONFIRMED");
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).isEmpty();

        FAILING_ACCOUNTS.remove(account);
        JsonNode retried = remit(body);

        assertThat(retried.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(retried);
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).hasSize(1);
    }

    @Test
    void aTransferWhoseAnswerWasLostCompletesOnTheRepeatAfterTheBankStartsRefusingTheAccount() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        SILENT_ACCOUNTS.add(account);
        String body = toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000");

        assertThat(post(body).statusCode()).isEqualTo(503);
        SILENT_ACCOUNTS.remove(account);
        REFUSED_ACCOUNTS.add(account);
        JsonNode retried = remit(body);

        assertThat(retried.get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(remit(body)).isEqualTo(retried);
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).hasSize(1);
    }

    @RepeatedTest(3)
    void concurrentCopiesOfOneBankRemittanceSendOneTransfer() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        String body = toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000");

        List<JsonNode> results = concurrently(() -> remit(body));

        assertThat(results).allSatisfy(result -> assertThat(result.get("remittanceStatus").asText()).isIn("TRANSFERRING", "COMPLETED"));
        assertThat(remit(body).get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).hasSize(1);
    }

    @Test
    void refusesABankAccountTheBankDoesNotKnow() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        INVALID_ACCOUNTS.add(account);

        for (String body : List.of(toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000"),
                toBank(UUID.randomUUID().toString(), sender, "", account, "7000"),
                "{\"requestId\":\"" + UUID.randomUUID() + "\",\"fromMembershipId\":\"" + sender + "\",\"remittanceType\":1,\"amount\":7000}")) {
            HttpResponse<String> refused = post(body);

            assertThat(refused.statusCode()).as(body).isEqualTo(400);
            assertThat(error(refused)).isEqualTo("INVALID_BANK_ACCOUNT");
        }
        assertThat(BALANCES.get(sender)).isEqualTo(20000);
        assertThat(transfersTo(account)).isEmpty();
    }

    @Test
    void listsTheRemittancesAMemberSentAndReceivedWithTheirStatus() throws Exception {
        String member = memberWithBalance(10000);
        String other = memberWithBalance(10000);
        String sent = remit(toMember(UUID.randomUUID().toString(), member, other, "1000")).get("remittanceId").asText();
        String failed = remit(toMember(UUID.randomUUID().toString(), member, other, "50000")).get("remittanceId").asText();
        String received = remit(toMember(UUID.randomUUID().toString(), other, member, "2000")).get("remittanceId").asText();
        remit(toMember(UUID.randomUUID().toString(), other, memberWithBalance(0), "500"));

        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(uri("/remittance/" + member)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, String> statuses = new ConcurrentHashMap<>();
        for (JsonNode remittance : JSON.readTree(response.body())) {
            statuses.put(remittance.get("remittanceId").asText(), remittance.get("remittanceStatus").asText());
        }
        assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(sent, "COMPLETED", failed, "FAILED", received, "COMPLETED"));
    }

    @Test
    void aRemittanceToAMemberReportsEachStepToItsFlowUnderItsRequestId() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();

        remit(toMember(requestId, sender, receiver, "4000"));

        List<JsonNode> steps = flowSteps(requestId, 3);
        assertThat(steps).extracting(RemittanceInvariantsTest::stepAndOutcome)
                .containsExactly("REMITTANCE_REQUESTED SUCCEEDED", "SENDER_DEBITED SUCCEEDED", "RECEIVER_CREDITED SUCCEEDED");
        assertThat(steps).allSatisfy(step -> {
            assertThat(step.get("flowType").asText()).isEqualTo("REMITTANCE");
            assertThat(step.get("service").asText()).isEqualTo("remittance-service");
            assertThat(step.get("membershipId").asText()).isEqualTo(sender);
            assertThat(step.get("amount").asLong()).isEqualTo(4000);
        });
    }

    @Test
    void aTransferTheBankRefusesReportsTheFailureAndTheRefundWithoutTheAccount() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        REFUSED_ACCOUNTS.add(account);
        String requestId = UUID.randomUUID().toString();

        remit(toBank(requestId, sender, "Jay Bank", account, "7000"));

        List<JsonNode> steps = flowSteps(requestId, 4);
        assertThat(steps).extracting(RemittanceInvariantsTest::stepAndOutcome).containsExactly(
                "REMITTANCE_REQUESTED SUCCEEDED", "SENDER_DEBITED SUCCEEDED", "BANK_TRANSFERRED FAILED", "SENDER_REFUNDED SUCCEEDED");
        assertThat(steps.get(2).get("reason").asText()).isEqualTo("The bank refused the transfer");
        assertThat(steps).allSatisfy(step -> assertThat(step.toString()).doesNotContain(account, "Jay Bank"));
    }

    @Test
    void aRemittanceAboveTheBalanceReportsTheRefusedDebit() throws Exception {
        String sender = memberWithBalance(1000);
        String requestId = UUID.randomUUID().toString();

        remit(toMember(requestId, sender, memberWithBalance(0), "5000"));

        List<JsonNode> steps = flowSteps(requestId, 2);
        assertThat(steps).extracting(RemittanceInvariantsTest::stepAndOutcome)
                .containsExactly("REMITTANCE_REQUESTED SUCCEEDED", "SENDER_DEBITED FAILED");
        assertThat(steps.get(1).get("reason").asText()).isEqualTo("money-service refused to take the amount from the sender's wallet");
    }

    @Test
    void aRefusedReceiverReportsTheRequestAsFailedWithTheReason() throws Exception {
        String sender = memberWithBalance(5000);
        String closed = memberWithBalance(0);
        INVALID.add(closed);
        String requestId = UUID.randomUUID().toString();

        assertThat(post(toMember(requestId, sender, closed, "1000")).statusCode()).isEqualTo(400);

        JsonNode step = flowSteps(requestId, 1).getFirst();
        assertThat(stepAndOutcome(step)).isEqualTo("REMITTANCE_REQUESTED FAILED");
        assertThat(step.get("reason").asText()).isEqualTo("The receiver does not exist or is not valid");
        assertThat(step.get("membershipId").asText()).isEqualTo(sender);
    }

    @Test
    void aDebitWithoutAnAnswerReportsEachStepOnceAfterTheRetryFinishesIt() throws Exception {
        String sender = memberWithBalance(10000);
        String requestId = UUID.randomUUID().toString();
        String body = toMember(requestId, sender, memberWithBalance(0), "2500");
        unanswered("-decrease", 1);

        assertThat(post(body).statusCode()).isEqualTo(503);
        remit(body);
        remit(body);

        assertThat(flowSteps(requestId, 3)).extracting(RemittanceInvariantsTest::stepAndOutcome)
                .containsExactly("REMITTANCE_REQUESTED SUCCEEDED", "SENDER_DEBITED SUCCEEDED", "RECEIVER_CREDITED SUCCEEDED");
    }

    @Test
    void aDebitWhoseAnswerWasLostIsCompletedByTheSweepWithoutARepeat() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();
        unanswered("-decrease", 1);
        assertThat(post(toMember(requestId, sender, receiver, "2500")).statusCode()).isEqualTo(503);
        assertThat(storedStatus(requestId)).isEqualTo("REQUESTED");

        sweep.resumeUnfinishedRemittances();

        assertThat(storedStatus(requestId)).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
        assertThat(flowSteps(requestId, 3)).extracting(RemittanceInvariantsTest::stepAndOutcome)
                .containsExactly("REMITTANCE_REQUESTED SUCCEEDED", "SENDER_DEBITED SUCCEEDED", "RECEIVER_CREDITED SUCCEEDED");
    }

    @Test
    void aCreditWithoutAnAnswerIsCompletedByTheSweepWithoutARepeat() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();
        unanswered("-increase", 1);
        assertThat(post(toMember(requestId, sender, receiver, "2500")).statusCode()).isEqualTo(503);
        assertThat(storedStatus(requestId)).isEqualTo("DEBITED");

        sweep.resumeUnfinishedRemittances();

        assertThat(storedStatus(requestId)).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void aTransferTheBankDidNotConfirmIsCompletedByTheSweepUnderTheSameTransferIdAndLandsOnce() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        String requestId = UUID.randomUUID().toString();
        SILENT_ACCOUNTS.add(account);
        assertThat(post(toBank(requestId, sender, "Jay Bank", account, "7000")).statusCode()).isEqualTo(503);

        sweep.resumeUnfinishedRemittances();
        assertThat(storedStatus(requestId)).isEqualTo("TRANSFERRING");
        SILENT_ACCOUNTS.remove(account);
        sweep.resumeUnfinishedRemittances();

        assertThat(storedStatus(requestId)).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        String transferId = "remittance-" + storedRemittanceId(requestId) + "-transfer";
        assertThat(transfersTo(account)).singleElement()
                .satisfies(transfer -> assertThat(transfer.get("requestId").asText()).isEqualTo(transferId));
        assertThat(ASKED.stream().filter(transfer -> transfer.get("toBankAccountNumber").asText().equals(account))
                .map(transfer -> transfer.get("requestId").asText())).containsExactly(transferId, transferId, transferId);
    }

    @Test
    void aRefundMoneyServiceFailedWith5xxIsRefundedByTheSweepWithoutARepeat() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        CREDIT_REFUSED.add(receiver);
        String requestId = UUID.randomUUID().toString();
        failing("-refund", 1);
        assertThat(post(toMember(requestId, sender, receiver, "2500")).statusCode()).isEqualTo(503);
        assertThat(storedStatus(requestId)).isEqualTo("REFUNDING");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);

        sweep.resumeUnfinishedRemittances();

        assertThat(storedStatus(requestId)).isEqualTo("REFUNDED");
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
        assertThat(BALANCES.get(receiver)).isZero();
    }

    @Test
    void aRefundThatAMoneyServiceWithoutTheRefundEndpointDidNotTakeIsRefundedByTheSweepAfterTheUpgrade() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        CREDIT_REFUSED.add(receiver);
        REFUND_UNKNOWN.add(sender);
        String requestId = UUID.randomUUID().toString();
        assertThat(post(toMember(requestId, sender, receiver, "2500")).statusCode()).isEqualTo(503);

        sweep.resumeUnfinishedRemittances();
        assertThat(storedStatus(requestId)).isEqualTo("REFUNDING");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        REFUND_UNKNOWN.remove(sender);
        sweep.resumeUnfinishedRemittances();

        assertThat(storedStatus(requestId)).isEqualTo("REFUNDED");
        assertThat(BALANCES.get(sender)).isEqualTo(10000);
    }

    @Test
    void theSweepGivesUpAfterItsAttemptsWithOneErrorAndARepeatStillFinishesTheRemittance(CapturedOutput output) throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();
        String body = toMember(requestId, sender, receiver, "2500");
        MONEY_SILENT.add(sender);
        assertThat(post(body).statusCode()).isEqualTo(503);
        String remittanceId = storedRemittanceId(requestId);
        String debitId = "remittance-" + remittanceId + "-decrease";

        for (int sweeps = 0; sweeps < 5; sweeps++) {
            sweep.resumeUnfinishedRemittances();
        }

        assertThat(MONEY_CALLS.stream().filter(debitId::equals)).hasSize(1 + 3);
        assertThat(storedStatus(requestId)).isEqualTo("REQUESTED");
        assertThat(output.getOut().lines().filter(line -> line.contains("ERROR") && line.contains(remittanceId))).hasSize(1);

        MONEY_SILENT.remove(sender);
        assertThat(remit(body).get("remittanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @Test
    void theSweepLeavesFinishedRemittancesAlone() throws Exception {
        String sender = memberWithBalance(10000);
        String refusing = memberWithBalance(0);
        CREDIT_REFUSED.add(refusing);
        List<String> finished = List.of(
                remit(toMember(UUID.randomUUID().toString(), sender, memberWithBalance(0), "1000")).get("remittanceId").asText(),
                remit(toMember(UUID.randomUUID().toString(), sender, memberWithBalance(0), "50000")).get("remittanceId").asText(),
                remit(toMember(UUID.randomUUID().toString(), sender, refusing, "1000")).get("remittanceId").asText());
        int calls = MONEY_CALLS.size();

        sweep.resumeUnfinishedRemittances();

        assertThat(List.copyOf(MONEY_CALLS.subList(calls, MONEY_CALLS.size())))
                .noneMatch(call -> finished.stream().anyMatch(call::contains));
        assertThat(BALANCES.get(sender)).isEqualTo(9000);
    }

    @RepeatedTest(5)
    void sweepsAndRepeatsOfALostDebitAtOnceMoveTheMoneyOnce() throws Exception {
        String sender = memberWithBalance(10000);
        String receiver = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();
        String body = toMember(requestId, sender, receiver, "2500");
        unanswered("-decrease", 1);
        assertThat(post(body).statusCode()).isEqualTo(503);

        List<JsonNode> repeats = sweepsAndRepeatsAtOnce(body);

        assertThat(repeats).hasSize(CONCURRENT_REQUESTS / 2)
                .allSatisfy(result -> assertThat(result.get("remittanceStatus").asText()).isEqualTo("COMPLETED"));
        assertThat(storedStatus(requestId)).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(7500);
        assertThat(BALANCES.get(receiver)).isEqualTo(2500);
    }

    @RepeatedTest(5)
    void sweepsAndRepeatsOfAnUnconfirmedTransferAtOnceLandItOnce() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        String requestId = UUID.randomUUID().toString();
        String body = toBank(requestId, sender, "Jay Bank", account, "7000");
        SILENT_ACCOUNTS.add(account);
        assertThat(post(body).statusCode()).isEqualTo(503);
        SILENT_ACCOUNTS.remove(account);

        List<JsonNode> repeats = sweepsAndRepeatsAtOnce(body);

        assertThat(repeats).hasSize(CONCURRENT_REQUESTS / 2)
                .allSatisfy(result -> assertThat(result.get("remittanceStatus").asText()).isEqualTo("COMPLETED"));
        assertThat(storedStatus(requestId)).isEqualTo("COMPLETED");
        assertThat(BALANCES.get(sender)).isEqualTo(13000);
        assertThat(transfersTo(account)).hasSize(1);
        assertThat(ASKED.stream().filter(transfer -> transfer.get("toBankAccountNumber").asText().equals(account))
                .map(transfer -> transfer.get("requestId").asText()).distinct()).hasSize(1);
    }

    private List<JsonNode> sweepsAndRepeatsAtOnce(String body) throws Exception {
        AtomicInteger turn = new AtomicInteger();
        return concurrently(() -> {
            if (turn.getAndIncrement() % 2 == 0) {
                sweep.resumeUnfinishedRemittances();
                return null;
            }
            return remit(body);
        }).stream().filter(result -> result != null).toList();
    }

    private String storedStatus(String requestId) {
        return database.queryForObject("SELECT remittance_status FROM remittance WHERE request_id = ?", String.class, requestId);
    }

    private String storedRemittanceId(String requestId) {
        return database.queryForObject("SELECT remittance_id::text FROM remittance WHERE request_id = ?", String.class, requestId);
    }

    private static List<JsonNode> flowSteps(String requestId, int count) {
        return KafkaTestBroker.awaitValues("jaypay.flow.events", requestId, values -> values.size() >= count, Duration.ofSeconds(30))
                .stream().map(RemittanceInvariantsTest::readJson).toList();
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

    private static void unanswered(String suffix, int times) {
        UNANSWERED.put(suffix, new AtomicInteger(times));
    }

    private static void failing(String suffix, int times) {
        FAILING.put(suffix, new AtomicInteger(times));
    }

    private static List<JsonNode> transfersTo(String account) {
        return LANDED.values().stream().filter(transfer -> transfer.get("toBankAccountNumber").asText().equals(account)).toList();
    }

    private static String toMember(String requestId, String sender, String receiver, String amount) {
        return "{\"requestId\":\"" + requestId + "\",\"fromMembershipId\":\"" + sender + "\",\"toMembershipId\":\"" + receiver
                + "\",\"remittanceType\":0,\"amount\":" + amount + "}";
    }

    private static String toBank(String requestId, String sender, String bank, String account, String amount) {
        return "{\"requestId\":\"" + requestId + "\",\"fromMembershipId\":\"" + sender + "\",\"toBankName\":\"" + bank
                + "\",\"toBankAccountNumber\":\"" + account + "\",\"remittanceType\":1,\"amount\":" + amount + "}";
    }

    private JsonNode history(String membershipId) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(uri("/remittance/" + membershipId)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private JsonNode remit(String body) throws Exception {
        HttpResponse<String> response = post(body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri("/remittance/request"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String error(HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body()).path("error").asText();
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
                if (UNKNOWN.contains(member)) {
                    respond(exchange, 404, "");
                    return;
                }
                respond(exchange, 200, "{\"membershipId\":\"" + member + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                        + "\"address\":\"Seocho\",\"valid\":" + !INVALID.contains(member) + ",\"corp\":false}");
            });
            server.createContext("/money/decrease", exchange -> changeMoney(exchange, -1));
            server.createContext("/money/increase", exchange -> changeMoney(exchange, 1));
            server.createContext("/money/refund", RemittanceInvariantsTest::refundMoney);
            server.createContext("/banking/info", exchange -> {
                JsonNode account = JSON.readTree(exchange.getRequestBody().readAllBytes());
                String number = account.get("bankAccountNumber").asText();
                respond(exchange, 200, INVALID_ACCOUNTS.contains(number) ? "" : "{\"bankName\":\"" + account.get("bankName").asText()
                        + "\",\"bankAccountNumber\":\"" + number + "\",\"valid\":true}");
            });
            server.createContext("/banking/firmbanking/request", exchange -> {
                JsonNode transfer = JSON.readTree(exchange.getRequestBody().readAllBytes());
                ASKED.add(transfer);
                String account = transfer.get("toBankAccountNumber").asText();
                if (FAILING_ACCOUNTS.contains(account)) {
                    respond(exchange, 500, "{\"status\":500}");
                    return;
                }
                String status = TRANSFER_RESULTS.computeIfAbsent(transfer.get("requestId").asText(), id -> {
                    if (REFUSED_ACCOUNTS.contains(account)) {
                        return "FAILED";
                    }
                    LANDED.put(id, transfer);
                    return "SUCCEEDED";
                });
                if (SILENT_ACCOUNTS.contains(account)) {
                    respond(exchange, 503, "{\"error\":\"TRANSFER_NOT_CONFIRMED\",\"message\":\"no answer\"}");
                    return;
                }
                respond(exchange, 200, "{\"requestId\":\"" + transfer.get("requestId").asText() + "\",\"moneyAmount\":"
                        + transfer.get("moneyAmount").asLong() + ",\"transferStatus\":\"" + status + "\"}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void changeMoney(HttpExchange exchange, int sign) throws IOException {
        JsonNode change = JSON.readTree(exchange.getRequestBody().readAllBytes());
        if (MONEY_SILENT.contains(change.get("targetMembershipId").asText())) {
            MONEY_CALLS.add(change.get("requestId").asText());
            respond(exchange, 500, "{\"status\":500}");
            return;
        }
        answer(exchange, change.get("requestId").asText(), id -> apply(id, change.get("targetMembershipId").asText(),
                sign, change.get("amount").asLong()));
    }

    private static void refundMoney(HttpExchange exchange) throws IOException {
        JsonNode refund = JSON.readTree(exchange.getRequestBody().readAllBytes());
        String requestId = refund.get("requestId").asText();
        JsonNode decrease = SUCCEEDED_DECREASES.get(refund.get("refundedRequestId").asText());
        if (decrease != null && REFUND_UNKNOWN.contains(decrease.get("member").asText())) {
            MONEY_CALLS.add(requestId);
            respond(exchange, 404, "{\"status\":404,\"error\":\"Not Found\"}");
            return;
        }
        if (decrease == null) {
            MONEY_CALLS.add(requestId);
            respond(exchange, 409, "{\"error\":\"NOT_REFUNDABLE\",\"message\":\"no decrease\"}");
            return;
        }
        long amount = decrease.get("amount").asLong();
        answer(exchange, requestId, id -> {
            BALANCES.merge(decrease.get("member").asText(), amount, Long::sum);
            return "{\"moneyChangingRequestId\":\"" + id + "\",\"moneyChangingType\":1,\"moneyChangingResultStatus\":1,\"amount\":" + amount + "}";
        });
    }

    private static void answer(HttpExchange exchange, String requestId, Function<String, String> firstResult) throws IOException {
        MONEY_CALLS.add(requestId);
        if (FAILING.entrySet().stream().anyMatch(rule -> requestId.endsWith(rule.getKey()) && rule.getValue().getAndDecrement() > 0)) {
            respond(exchange, 500, "{\"status\":500}");
            return;
        }
        String result = MONEY_RESULTS.computeIfAbsent(requestId, firstResult);
        boolean dropAnswer = UNANSWERED.entrySet().stream()
                .anyMatch(rule -> requestId.endsWith(rule.getKey()) && rule.getValue().getAndDecrement() > 0);
        respond(exchange, dropAnswer ? 503 : 200, dropAnswer ? "" : result);
    }

    private static String apply(String requestId, String member, int sign, long amount) {
        boolean[] applied = {false};
        if (INVALID.contains(member)) {
            return "{\"moneyChangingRequestId\":\"" + requestId + "\",\"moneyChangingType\":" + (sign > 0 ? 1 : 2)
                    + ",\"moneyChangingResultStatus\":2,\"amount\":" + amount + "}";
        }
        if (sign > 0 && !CREDIT_REFUSED.contains(member)) {
            BALANCES.merge(member, amount, Long::sum);
            applied[0] = true;
        }
        if (sign < 0) {
            BALANCES.computeIfPresent(member, (id, balance) -> {
                if (balance < amount) {
                    return balance;
                }
                applied[0] = true;
                return balance - amount;
            });
            if (applied[0]) {
                SUCCEEDED_DECREASES.put(requestId, JSON.createObjectNode().put("member", member).put("amount", amount));
            }
        }
        return "{\"moneyChangingRequestId\":\"" + requestId + "\",\"moneyChangingType\":" + (sign > 0 ? 1 : 2)
                + ",\"moneyChangingResultStatus\":" + (applied[0] ? 1 : 2) + ",\"amount\":" + amount + "}";
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
