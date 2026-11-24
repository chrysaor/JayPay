package com.jaypay.money.aggregation.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipServiceAdapterTest {

    private static final String MEMBERS_IN_SEOCHO =
            "[{\"membershipId\":\"7\",\"name\":\"Payer\",\"email\":\"payer@example.com\",\"address\":\"Seocho\","
                    + "\"refreshToken\":\"\",\"valid\":true,\"corp\":false},"
                    + "{\"membershipId\":\"8\",\"name\":\"Payee\",\"email\":\"payee@example.com\",\"address\":\"Seocho\","
                    + "\"refreshToken\":\"\",\"valid\":true,\"corp\":true}]";

    private HttpServer membershipService;
    private MembershipServiceAdapter adapter;

    @BeforeEach
    void startMembershipService() throws IOException {
        membershipService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        membershipService.createContext("/", exchange -> {
            boolean known = exchange.getRequestURI().getPath().equals("/memberships/address/Seocho");
            byte[] body = (known ? MEMBERS_IN_SEOCHO : "{\"status\":404}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(known ? 200 : 404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        membershipService.start();
        adapter = new MembershipServiceAdapter(
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP),
                "http://127.0.0.1:" + membershipService.getAddress().getPort());
    }

    @AfterEach
    void stopMembershipService() {
        membershipService.stop(0);
    }

    @Test
    void listsTheMembershipIdsAtAnAddress() {
        assertThat(adapter.getMembershipByAddress("Seocho")).containsExactly("7", "8");
    }
}
