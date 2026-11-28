package com.jaypay.money.query;

import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.WalletRechargedEvent;
import com.jaypay.money.query.adapter.out.aws.dynamodb.DynamoDbLocal;
import com.sun.net.httpserver.HttpServer;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.StreamingEventProcessor;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegionTotalTest {

    private static final String PROCESSING_GROUP = "com.jaypay.money.query.adapter.axon";
    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final AtomicInteger MEMBERS = new AtomicInteger(700);
    private static final DynamoDbLocal DYNAMODB = DynamoDbLocal.withTable("region-total-test");
    private static final int FAILURES = 2;
    private static final Map<String, AtomicInteger> FAILING = new ConcurrentHashMap<>();
    private static final Set<String> UNKNOWN = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicInteger> ASKED = new ConcurrentHashMap<>();
    private static final HttpServer MEMBERSHIP = startMembership();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventGateway events;

    @Autowired
    private EventProcessingConfiguration processing;

    @DynamicPropertySource
    static void otherServices(DynamicPropertyRegistry registry) {
        registry.add("service.membership.url", () -> "http://127.0.0.1:" + MEMBERSHIP.getAddress().getPort());
        registry.add("dynamodb.table-name", DYNAMODB::tableName);
        registry.add("region-total.membership-patience", () -> "1s");
        registry.add("region-total.membership-retry-after", () -> "50ms");
    }

    @Test
    void aRechargeTheSagaRollsBackAfterTheBankConfirmedItStaysOutOfTheRegionTotal() throws Exception {
        String member = newMember();
        String rechargeId = UUID.randomUUID().toString();

        events.publish(bankConfirmed(rechargeId, member, 3000));
        events.publish(new RollbackFirmBankingFinishedEvent(UUID.randomUUID().toString(), member, UUID.randomUUID().toString()));
        waitUntilEarlierEventsAreHandled();

        assertThat(regionTotal(member)).isZero();
    }

    @Test
    void aRechargeThatIncreasedTheWalletIsCountedOnceWhenItsEventArrivesTwice() throws Exception {
        String member = newMember();
        String rechargeId = UUID.randomUUID().toString();

        events.publish(bankConfirmed(rechargeId, member, 3000));
        events.publish(new WalletRechargedEvent(rechargeId, member, 3000));
        events.publish(new WalletRechargedEvent(rechargeId, member, 3000));
        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), member, 500));
        waitUntilEarlierEventsAreHandled();

        assertThat(regionTotal(member)).isEqualTo(3500);
    }

    @Test
    void handlingEveryEventAgainAfterTheTrackingTokenIsLostLeavesTheRegionTotal() throws Exception {
        String member = newMember();
        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), member, 2000));
        waitUntilEarlierEventsAreHandled();
        assertThat(regionTotal(member)).isEqualTo(2000);

        StreamingEventProcessor processor = processing.eventProcessorByProcessingGroup(PROCESSING_GROUP, StreamingEventProcessor.class).orElseThrow();
        processor.shutDown();
        processor.resetTokens();
        processor.start();
        waitUntilEarlierEventsAreHandled();

        assertThat(regionTotal(member)).isEqualTo(2000);
    }

    @Test
    void aRechargeHandledWhileMembershipServiceFailsIsCountedOnceMembershipServiceAnswersAgain() throws Exception {
        String member = newMember();
        FAILING.put(member, new AtomicInteger(FAILURES));

        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), member, 3000));
        waitUntilEarlierEventsAreHandled();

        assertThat(regionTotal(member)).isEqualTo(3000);
        assertThat(ASKED.get(member)).hasValue(FAILURES + 1);
    }

    @Test
    void aRechargeMembershipServiceDoesNotAnswerForInTimeIsLeftOutAndTheNextRechargesAreCounted() throws Exception {
        String member = newMember();
        String next = newMember();
        FAILING.put(member, new AtomicInteger(Integer.MAX_VALUE));

        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), member, 3000));
        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), next, 700));
        waitUntilEarlierEventsAreHandled();

        assertThat(ASKED.get(member)).hasValueGreaterThan(FAILURES).hasValueLessThan(40);
        assertThat(regionTotal(member)).isZero();
        assertThat(regionTotal(next)).isEqualTo(700);
    }

    @Test
    void aRechargeOfAMemberThatDoesNotExistIsLeftOutWithoutAskingAgain() throws Exception {
        String member = newMember();
        UNKNOWN.add(member);

        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), member, 3000));
        waitUntilEarlierEventsAreHandled();

        assertThat(ASKED.get(member)).hasValue(1);
        assertThat(regionTotal(member)).isZero();
    }

    private void waitUntilEarlierEventsAreHandled() throws Exception {
        String marker = newMember();
        events.publish(new WalletRechargedEvent(UUID.randomUUID().toString(), marker, 1));
        long until = System.nanoTime() + PATIENCE.toNanos();
        while (regionTotal(marker) != 1) {
            if (System.nanoTime() > until) {
                throw new AssertionError("money-query-service did not handle the events within " + PATIENCE);
            }
            Thread.sleep(50);
        }
    }

    private long regionTotal(String member) throws Exception {
        String body = mockMvc.perform(get("/money/query/get-money-sum-by-address/" + addressOf(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body);
    }

    private static RequestFirmBankingFinishedEvent bankConfirmed(String rechargeId, String member, long amount) {
        return new RequestFirmBankingFinishedEvent(UUID.randomUUID().toString(), rechargeId, member, "jaypay-company", "123456789",
                amount, RequestFirmBankingFinishedEvent.SUCCEEDED, UUID.randomUUID().toString());
    }

    private static String newMember() {
        return String.valueOf(MEMBERS.incrementAndGet());
    }

    private static String addressOf(String member) {
        return "Region-" + member;
    }

    private static HttpServer startMembership() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/memberships/", exchange -> {
                String member = exchange.getRequestURI().getPath().substring("/memberships/".length());
                ASKED.computeIfAbsent(member, asked -> new AtomicInteger()).incrementAndGet();
                AtomicInteger failures = FAILING.get(member);
                if (UNKNOWN.contains(member) || failures != null && failures.getAndDecrement() > 0) {
                    exchange.sendResponseHeaders(UNKNOWN.contains(member) ? 404 : 503, -1);
                    exchange.close();
                    return;
                }
                byte[] body = ("{\"membershipId\":\"" + member + "\",\"name\":\"Member\",\"email\":\"member@example.com\","
                        + "\"address\":\"" + addressOf(member) + "\",\"valid\":true,\"corp\":false}").getBytes(StandardCharsets.UTF_8);
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

    @TestConfiguration
    static class LocalDynamoDb {
        @Bean
        @Primary
        DynamoDbClient localDynamoDbClient() {
            return DYNAMODB.client();
        }
    }
}
