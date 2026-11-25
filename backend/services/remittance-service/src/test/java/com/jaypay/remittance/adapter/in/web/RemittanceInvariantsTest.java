package com.jaypay.remittance.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "remittance.amount.max=10000000")
@ActiveProfiles("test")
class RemittanceInvariantsTest {

    private static final int CONCURRENT_REQUESTS = 16;
    private static final AtomicLong MEMBERS = new AtomicLong(9000);
    private static final Set<String> INVALID = ConcurrentHashMap.newKeySet();
    private static final Set<String> UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final Set<String> CREDIT_REFUSED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> BALANCES = new ConcurrentHashMap<>();
    private static final Map<String, String> MONEY_RESULTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> UNANSWERED = new ConcurrentHashMap<>();
    private static final List<String> MONEY_CALLS = new CopyOnWriteArrayList<>();
    private static final Set<String> INVALID_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REFUSED_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SILENT_ACCOUNTS = ConcurrentHashMap.newKeySet();
    private static final List<JsonNode> TRANSFERS = new CopyOnWriteArrayList<>();
    private static final HttpServer JAYPAY = startFakeJayPay();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

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
        assertThat(transfersTo(account)).hasSize(1);
    }

    @Test
    void aTransferTheBankDoesNotAnswerIsNeverSentAgain() throws Exception {
        String sender = memberWithBalance(20000);
        String account = "1002-" + sender;
        SILENT_ACCOUNTS.add(account);
        String body = toBank(UUID.randomUUID().toString(), sender, "Jay Bank", account, "7000");

        JsonNode remittance = remit(body);
        JsonNode retried = remit(body);

        assertThat(remittance.get("remittanceStatus").asText()).isEqualTo("TRANSFERRING");
        assertThat(retried).isEqualTo(remittance);
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

    private static String memberWithBalance(long balance) {
        String member = String.valueOf(MEMBERS.incrementAndGet());
        BALANCES.put(member, balance);
        return member;
    }

    private static void unanswered(String suffix, int times) {
        UNANSWERED.put(suffix, new AtomicInteger(times));
    }

    private static List<JsonNode> transfersTo(String account) {
        return TRANSFERS.stream().filter(transfer -> transfer.get("toBankAccountNumber").asText().equals(account)).toList();
    }

    private static String toMember(String requestId, String sender, String receiver, String amount) {
        return "{\"requestId\":\"" + requestId + "\",\"fromMembershipId\":\"" + sender + "\",\"toMembershipId\":\"" + receiver
                + "\",\"remittanceType\":0,\"amount\":" + amount + "}";
    }

    private static String toBank(String requestId, String sender, String bank, String account, String amount) {
        return "{\"requestId\":\"" + requestId + "\",\"fromMembershipId\":\"" + sender + "\",\"toBankName\":\"" + bank
                + "\",\"toBankAccountNumber\":\"" + account + "\",\"remittanceType\":1,\"amount\":" + amount + "}";
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
            server.createContext("/banking/info", exchange -> {
                JsonNode account = JSON.readTree(exchange.getRequestBody().readAllBytes());
                String number = account.get("bankAccountNumber").asText();
                respond(exchange, 200, INVALID_ACCOUNTS.contains(number) ? "" : "{\"bankName\":\"" + account.get("bankName").asText()
                        + "\",\"bankAccountNumber\":\"" + number + "\",\"valid\":true}");
            });
            server.createContext("/banking/firmbanking/request", exchange -> {
                JsonNode transfer = JSON.readTree(exchange.getRequestBody().readAllBytes());
                TRANSFERS.add(transfer);
                String account = transfer.get("toBankAccountNumber").asText();
                if (SILENT_ACCOUNTS.contains(account)) {
                    respond(exchange, 504, "");
                    return;
                }
                respond(exchange, 200, "{\"firmBankingRequestId\":\"1\",\"moneyAmount\":" + transfer.get("moneyAmount").asLong()
                        + ",\"firmBankingStatus\":" + (REFUSED_ACCOUNTS.contains(account) ? 2 : 1) + "}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void changeMoney(HttpExchange exchange, int sign) throws IOException {
        JsonNode change = JSON.readTree(exchange.getRequestBody().readAllBytes());
        String requestId = change.get("requestId").asText();
        MONEY_CALLS.add(requestId);
        String result = MONEY_RESULTS.computeIfAbsent(requestId, id -> apply(id, change.get("targetMembershipId").asText(),
                sign, change.get("amount").asLong()));
        boolean dropAnswer = UNANSWERED.entrySet().stream()
                .anyMatch(rule -> requestId.endsWith(rule.getKey()) && rule.getValue().getAndDecrement() > 0);
        respond(exchange, dropAnswer ? 503 : 200, dropAnswer ? "" : result);
    }

    private static String apply(String requestId, String member, int sign, long amount) {
        boolean[] applied = {false};
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
