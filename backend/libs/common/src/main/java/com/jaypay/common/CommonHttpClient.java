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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class CommonHttpClient {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public CommonHttpClient(MeterRegistry meterRegistry, ObservationRegistry observationRegistry,
                            @Value("${jaypay.http.connect-timeout}") Duration connectTimeout,
                            @Value("${jaypay.http.request-timeout}") Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        httpClient = MicrometerHttpClient.instrumentationBuilder(HttpClient.newBuilder().connectTimeout(connectTimeout).build(), meterRegistry)
                .observationRegistry(observationRegistry)
                .build();
    }

    public <T> HttpReply<T> get(String url, Class<T> responseType) {
        return send(request(url).GET(), JSON.constructType(responseType));
    }

    public <T> HttpReply<T> get(String url, TypeReference<T> responseType) {
        return send(request(url).GET(), JSON.constructType(responseType));
    }

    public <T> HttpReply<T> post(String url, Object body, Class<T> responseType) {
        return send(jsonPost(url, body), JSON.constructType(responseType));
    }

    public <T> HttpReply<T> post(String url, Object body, TypeReference<T> responseType) {
        return send(jsonPost(url, body), JSON.constructType(responseType));
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout);
    }

    private HttpRequest.Builder jsonPost(String url, Object body) {
        try {
            return request(url)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> HttpReply<T> send(HttpRequest.Builder request, JavaType responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            boolean readable = response.statusCode() / 100 == 2 && !response.body().isBlank();
            return new HttpReply<>(response.statusCode(), readable ? JSON.readValue(response.body(), responseType) : null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
