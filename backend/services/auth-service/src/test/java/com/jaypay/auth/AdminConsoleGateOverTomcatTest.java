package com.jaypay.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminConsoleGateOverTomcatTest {

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        StaffTokens.register(registry);
    }

    @Test
    void letsAnAllowedRequestThrough() throws Exception {
        assertThat(statusOf("/authorize/api/membership/memberships/1")).isEqualTo(204);
    }

    @ParameterizedTest
    @CsvSource({
            "/authorize/api/membership/memberships/1/..%2F..%2Fregister",
            "/authorize/api/membership/memberships/1/..%5C..%5Cregister",
            "/authorize/api/membership/memberships/1/%2E%2E/register",
            "/authorize/api/membership/memberships/1;jsessionid=x",
            "/authorize/api/membership//memberships/1",
            "/authorize/api/membership/memberships/1/../../register",
            "/authorize/api/membership/memberships/%251",
    })
    void answersAnAmbiguousPathWithAStatusNginxPassesOn(String path) throws Exception {
        assertThat(statusOf(path)).isEqualTo(403);
    }

    private int statusOf(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(at(path)).header("Authorization", "Bearer " + StaffTokens.of("admin").value()).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI at(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
