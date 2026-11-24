package com.jaypay.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java11.instrument.binder.jdk.MicrometerHttpClient;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class CommonHttpClient {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final HttpClient httpClient;

    public CommonHttpClient(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        httpClient = MicrometerHttpClient.instrumentationBuilder(HttpClient.newHttpClient(), meterRegistry)
                .observationRegistry(observationRegistry)
                .build();
    }

    public <T> T get(String url, Class<T> responseType) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), JSON.constructType(responseType));
    }

    public <T> T get(String url, TypeReference<T> responseType) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), JSON.constructType(responseType));
    }

    public <T> T post(String url, Object body, Class<T> responseType) {
        return send(jsonPost(url, body), JSON.constructType(responseType));
    }

    public <T> T post(String url, Object body, TypeReference<T> responseType) {
        return send(jsonPost(url, body), JSON.constructType(responseType));
    }

    public void post(String url, Object body) {
        send(jsonPost(url, body), null);
    }

    private HttpRequest.Builder jsonPost(String url, Object body) {
        try {
            return HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T send(HttpRequest.Builder request, JavaType responseType) {
        try {
            String body = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
            return responseType == null || body.isBlank() ? null : JSON.readValue(body, responseType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public HttpResponse<String> sendGetRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> sendPostRequest(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
