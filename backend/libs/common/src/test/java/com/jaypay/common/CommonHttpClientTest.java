package com.jaypay.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.membership.MembershipResponse;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CommonHttpClientTest {

    private static final String MEMBER =
            "{\"membershipId\":\"7\",\"name\":\"Payer\",\"email\":\"payer@example.com\",\"address\":\"Seocho\","
                    + "\"refreshToken\":\"secret\",\"valid\":true,\"corp\":false,\"addedLater\":1}";

    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private HttpServer server;
    private CommonHttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/member", exchange -> respond(exchange, MEMBER));
        server.createContext("/members", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "[" + MEMBER + "]");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsAResponseThatCarriesFieldsTheCallerDoesNotKnow() {
        MembershipResponse member = client.get(baseUrl + "/member", MembershipResponse.class);

        assertThat(member).isEqualTo(new MembershipResponse("7", "Payer", "payer@example.com", "Seocho", true, false));
    }

    @Test
    void postsTheBodyAsJsonAndReadsAListBack() {
        List<MembershipResponse> members = client.post(baseUrl + "/members", Map.of("address", "Seocho"), new TypeReference<>() {
        });

        assertThat(receivedBody.get()).isEqualTo("{\"address\":\"Seocho\"}");
        assertThat(members).extracting(MembershipResponse::membershipId).containsExactly("7");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
