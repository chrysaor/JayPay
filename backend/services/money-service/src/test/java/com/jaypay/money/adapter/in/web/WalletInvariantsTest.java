package com.jaypay.money.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "money.amount.max=2000000000")
@ActiveProfiles("test")
class WalletInvariantsTest {

    private static final int CONCURRENT_REQUESTS = 40;
    private static final AtomicLong MEMBERS = new AtomicLong(5000);
    private static final Set<String> UNANSWERED = ConcurrentHashMap.newKeySet();
    private static final HttpServer MEMBERSHIP = startMembershipService();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void membershipService(DynamicPropertyRegistry registry) {
        registry.add("service.membership.url", () -> "http://127.0.0.1:" + MEMBERSHIP.getAddress().getPort());
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @AfterAll
    static void stopMembershipService() {
        MEMBERSHIP.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1000", "0", "2000000001", "1.5", "\"ten\"", "null", "9223372036854775808"})
    void refusesAnAmountThatIsNotAPositiveWholeNumberWithinTheLimitAndChangesNothing(String amount) throws Exception {
        String member = memberWithBalance(5000);

        for (String path : List.of("/money/increase", "/money/decrease")) {
            HttpResponse<String> refused = post(path, "{\"requestId\":\"" + UUID.randomUUID()
                    + "\",\"targetMembershipId\":\"" + member + "\",\"amount\":" + amount + "}");

            assertThat(refused.statusCode()).isEqualTo(400);
            assertThat(JSON.readTree(refused.body()).get("error").asText()).isIn("INVALID_AMOUNT", "INVALID_REQUEST");
        }
        assertThat(balanceOf(member)).isEqualTo(5000);
        assertThat(recordedChangesOf(member)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"requestId\":\"\",", "\"requestId\":\"has spaces\","})
    void refusesAChangeWithoutAUsableRequestId(String requestId) throws Exception {
        String member = memberWithBalance(5000);

        HttpResponse<String> refused = post("/money/increase",
                "{" + requestId + "\"targetMembershipId\":\"" + member + "\",\"amount\":1000}");

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(refused.body()).get("error").asText()).isEqualTo("INVALID_REQUEST_ID");
        assertThat(balanceOf(member)).isEqualTo(5000);
    }

    @RepeatedTest(5)
    void concurrentFirstTopUpsOfOneMemberAllCountAndShareOneWallet() throws Exception {
        String member = String.valueOf(MEMBERS.incrementAndGet());

        List<JsonNode> results = concurrently(() -> change("/money/increase", UUID.randomUUID().toString(), member, 1000));

        assertThat(results).allSatisfy(result -> assertThat(result.get("moneyChangingResultStatus").asInt()).isEqualTo(1));
        assertThat(balanceOf(member)).isEqualTo(CONCURRENT_REQUESTS * 1000L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM wallet WHERE membership_id = ?", Long.class, Long.parseLong(member)))
                .isEqualTo(1);
    }

    @Test
    void refusesADecreaseBeyondTheBalanceAndKeepsTheBalance() throws Exception {
        String member = memberWithBalance(3000);

        JsonNode refused = change("/money/decrease", UUID.randomUUID().toString(), member, 5000);

        assertThat(refused.get("moneyChangingType").asInt()).isEqualTo(2);
        assertThat(refused.get("moneyChangingResultStatus").asInt()).isEqualTo(2);
        assertThat(balanceOf(member)).isEqualTo(3000);
    }

    @Test
    void lowersTheBalanceByTheAmount() throws Exception {
        String member = memberWithBalance(3000);

        JsonNode lowered = change("/money/decrease", UUID.randomUUID().toString(), member, 1200);

        assertThat(lowered.get("moneyChangingResultStatus").asInt()).isEqualTo(1);
        assertThat(lowered.get("amount").asLong()).isEqualTo(1200);
        assertThat(balanceOf(member)).isEqualTo(1800);
    }

    @RepeatedTest(5)
    void concurrentDecreasesNeverTakeTheBalanceBelowZero() throws Exception {
        String member = memberWithBalance(10000);

        List<JsonNode> results = concurrently(() -> change("/money/decrease", UUID.randomUUID().toString(), member, 1000));

        assertThat(results.stream().filter(result -> result.get("moneyChangingResultStatus").asInt() == 1)).hasSize(10);
        assertThat(balanceOf(member)).isZero();
    }

    @RepeatedTest(5)
    void aRepeatedRequestIsAppliedOnceAndAnswersWithTheFirstResult() throws Exception {
        String member = memberWithBalance(0);
        String requestId = UUID.randomUUID().toString();

        JsonNode first = change("/money/increase", requestId, member, 1000);
        List<JsonNode> repeats = concurrently(() -> change("/money/increase", requestId, member, 1000));

        assertThat(repeats).allSatisfy(repeat -> assertThat(repeat).isEqualTo(first));
        assertThat(balanceOf(member)).isEqualTo(1000);
    }

    @Test
    void concurrentCopiesOfOneDecreaseLowerTheBalanceOnce() throws Exception {
        String member = memberWithBalance(5000);
        String requestId = UUID.randomUUID().toString();

        List<JsonNode> results = concurrently(() -> change("/money/decrease", requestId, member, 1000));

        assertThat(results).allSatisfy(result -> assertThat(result).isEqualTo(results.getFirst()));
        assertThat(balanceOf(member)).isEqualTo(4000);
    }

    @Test
    void refusesARequestIdReusedForADifferentChange() throws Exception {
        String member = memberWithBalance(5000);
        String requestId = UUID.randomUUID().toString();
        change("/money/increase", requestId, member, 1000);

        HttpResponse<String> reused = post("/money/increase", "{\"requestId\":\"" + requestId
                + "\",\"targetMembershipId\":\"" + member + "\",\"amount\":2000}");

        assertThat(reused.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(reused.body()).get("error").asText()).isEqualTo("REQUEST_ID_REUSED");
        assertThat(balanceOf(member)).isEqualTo(6000);
    }

    @Test
    void keepsBalancesBeyondTheIntRange() throws Exception {
        String member = memberWithBalance(0);

        change("/money/increase", UUID.randomUUID().toString(), member, 2_000_000_000L);
        change("/money/increase", UUID.randomUUID().toString(), member, 2_000_000_000L);

        assertThat(balanceOf(member)).isEqualTo(4_000_000_000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/money/increase", "/money/decrease"})
    void aChangeAskedWhileMembershipServiceFailsIsAppliedOnceWhenTheSameRequestComesBack(String path) throws Exception {
        String member = memberWithBalance(5000);
        String requestId = UUID.randomUUID().toString();

        UNANSWERED.add(member);
        HttpResponse<String> duringOutage = post(path, "{\"requestId\":\"" + requestId + "\",\"targetMembershipId\":\"" + member
                + "\",\"amount\":3000}");
        UNANSWERED.remove(member);

        assertThat(duringOutage.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(duringOutage.body()).get("error").asText()).isEqualTo("MEMBERSHIP_NOT_CONFIRMED");
        assertThat(balanceOf(member)).isEqualTo(5000);

        JsonNode afterRecovery = change(path, requestId, member, 3000);
        JsonNode repeated = change(path, requestId, member, 3000);

        assertThat(afterRecovery.get("moneyChangingResultStatus").asInt()).isEqualTo(1);
        assertThat(repeated).isEqualTo(afterRecovery);
        assertThat(balanceOf(member)).isEqualTo(path.endsWith("increase") ? 8000 : 2000);
    }

    private String memberWithBalance(long balance) throws Exception {
        String member = String.valueOf(MEMBERS.incrementAndGet());
        post("/money/create-member-money", "{\"membershipId\":\"" + member + "\"}");
        if (balance > 0) {
            change("/money/increase", UUID.randomUUID().toString(), member, balance);
        }
        assertThat(balanceOf(member)).isEqualTo(balance);
        return member;
    }

    private long balanceOf(String member) throws Exception {
        JsonNode wallets = JSON.readTree(post("/money/member-money", "{\"targetMembershipIds\":[\"" + member + "\"]}").body());
        return wallets.isEmpty() ? 0 : wallets.get(0).get("balance").asLong();
    }

    private long recordedChangesOf(String member) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM wallet_change WHERE membership_id = ?", Long.class, Long.parseLong(member));
    }

    private JsonNode change(String path, String requestId, String member, long amount) throws Exception {
        HttpResponse<String> response = post(path, "{\"requestId\":\"" + requestId + "\",\"targetMembershipId\":\"" + member
                + "\",\"amount\":" + amount + "}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
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

    private static HttpServer startMembershipService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(CONCURRENT_REQUESTS));
            server.createContext("/memberships/", exchange -> {
                String membershipId = exchange.getRequestURI().getPath().substring("/memberships/".length());
                if (UNANSWERED.contains(membershipId)) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                byte[] body = ("{\"membershipId\":\"" + membershipId + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                        + "\"address\":\"Seocho\",\"valid\":true,\"corp\":false,\"refreshToken\":\"\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
