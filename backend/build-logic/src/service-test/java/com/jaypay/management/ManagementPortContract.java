package com.jaypay.management;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ManagementPortContract {

    private static final String UNMAPPED = "/no-such-path";

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void reportsHealthAndTheProbesOnTheManagementPort(String path) throws Exception {
        HttpResponse<String> response = get(managementPort, path);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void servesPrometheusMetricsOnTheManagementPort() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/v3/api-docs", "/swagger-ui/index.html"})
    void servesNothingElseOnTheManagementPort(String path) throws Exception {
        assertThat(get(managementPort, path).statusCode())
                .isEqualTo(get(managementPort, UNMAPPED).statusCode())
                .isIn(401, 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator", "/actuator/health", "/actuator/health/liveness", "/actuator/prometheus",
            "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html",
    })
    void servesNeitherActuatorNorTheApiDocsOnTheServicePort(String path) throws Exception {
        assertThat(get(serverPort, path).statusCode())
                .isEqualTo(get(serverPort, UNMAPPED).statusCode())
                .isIn(401, 404);
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
