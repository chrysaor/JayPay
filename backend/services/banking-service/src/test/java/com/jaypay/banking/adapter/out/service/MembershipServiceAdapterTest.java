package com.jaypay.banking.adapter.out.service;

import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.banking.domain.MembershipNotConfirmedException;
import com.jaypay.common.CommonHttpClient;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipServiceAdapterTest {

    private HttpServer membershipService;
    private MembershipServiceAdapter adapter;

    @BeforeEach
    void startMembershipService() throws IOException {
        membershipService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        respond("/memberships/1", """
                {"membershipId":"1","name":"Kim","email":"kim@example.com","address":"Seoul","valid":true,"corp":false,"refreshToken":"t"}
                """);
        respond("/memberships/2", """
                {"membershipId":"2","name":"Lee","email":"lee@example.com","address":"Busan","valid":false,"corp":false}
                """);
        membershipService.start();
        adapter = new MembershipServiceAdapter(
                new CommonHttpClient(new SimpleMeterRegistry(), ObservationRegistry.NOOP, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                "http://localhost:" + membershipService.getAddress().getPort());
    }

    @AfterEach
    void stopMembershipService() {
        membershipService.stop(0);
    }

    @Test
    void reportsAValidMembership() {
        MembershipStatus status = adapter.getMembership("1");

        assertThat(status.getMembershipId()).isEqualTo("1");
        assertThat(status.isValid()).isTrue();
    }

    @Test
    void reportsAMembershipThatIsNotValidAsNotValid() {
        assertThat(adapter.getMembership("2").isValid()).isFalse();
    }

    @Test
    void reportsAMemberThatDoesNotExistAsNotValid() {
        respond("/memberships/3", 404, "");

        assertThat(adapter.getMembership("3").isValid()).isFalse();
    }

    @Test
    void anAnswerOf5xxIsNotAMemberThatIsNotValid() {
        respond("/memberships/4", 503, "");

        assertThatThrownBy(() -> adapter.getMembership("4")).isInstanceOf(MembershipNotConfirmedException.class);
    }

    @Test
    void noAnswerIsNotAMemberThatIsNotValid() {
        membershipService.stop(0);

        assertThatThrownBy(() -> adapter.getMembership("1")).isInstanceOf(MembershipNotConfirmedException.class);
    }

    private void respond(String path, String body) {
        respond(path, 200, body);
    }

    private void respond(String path, int status, String body) {
        membershipService.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }
}
