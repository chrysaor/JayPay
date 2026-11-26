package com.jaypay.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.membership.MembershipResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonHttpClientTest {

    private static final String MEMBER =
            "{\"membershipId\":\"7\",\"name\":\"Payer\",\"email\":\"payer@example.com\",\"address\":\"Seocho\","
                    + "\"refreshToken\":\"secret\",\"valid\":true,\"corp\":false,\"addedLater\":1}";

    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;
    private CommonHttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/member", exchange -> respond(exchange, 200, MEMBER));
        server.createContext("/members", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "[" + MEMBER + "]");
        });
        server.createContext("/conflict", exchange -> respond(exchange, 409, "{\"error\":\"REQUEST_ID_REUSED\",\"message\":\"no\"}"));
        server.createContext("/slow", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, MEMBER);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP, Duration.ofSeconds(1), Duration.ofMillis(300));
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void readsAResponseThatCarriesFieldsTheCallerDoesNotKnow() {
        HttpReply<MembershipResponse> reply = client.get(baseUrl + "/member", MembershipResponse.class);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.isSuccessful()).isTrue();
        assertThat(reply.body()).isEqualTo(new MembershipResponse("7", "Payer", "payer@example.com", "Seocho", true, false));
    }

    @Test
    void postsTheBodyAsJsonAndReadsAListBack() {
        HttpReply<List<MembershipResponse>> reply = client.post(baseUrl + "/members", Map.of("address", "Seocho"), new TypeReference<>() {
        });

        assertThat(receivedBody.get()).isEqualTo("{\"address\":\"Seocho\"}");
        assertThat(reply.body()).extracting(MembershipResponse::membershipId).containsExactly("7");
    }

    @Test
    void handsTheStatusOfARefusalToTheCallerWithoutReadingTheErrorAsTheAnswer() {
        HttpReply<MembershipResponse> reply = client.post(baseUrl + "/conflict", Map.of(), MembershipResponse.class);

        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.isSuccessful()).isFalse();
        assertThat(reply.body()).isNull();
    }

    @Test
    void givesUpOnAnAnswerThatTakesLongerThanTheRequestTimeout() {
        long started = System.nanoTime();

        assertThatThrownBy(() -> client.get(baseUrl + "/slow", MembershipResponse.class))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
