package com.jaypay.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "local"})
class LocalProfileApiDocsTest {

    @LocalServerPort
    private int serverPort;

    @Test
    void servesTheApiDocsAndSwaggerUiOnTheServicePortUnderTheLocalProfile() throws Exception {
        assertThat(get("/v3/api-docs").body()).contains("\"openapi\"", "/payment/request");
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
