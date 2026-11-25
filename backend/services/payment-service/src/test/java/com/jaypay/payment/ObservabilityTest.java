package com.jaypay.payment;

import com.jaypay.payment.application.port.out.GetMembershipPort;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ObservabilityTest {

    private static final InMemoryLogRecordExporter LOGS = InMemoryLogRecordExporter.create();
    private static final AtomicReference<String> RECEIVED_TRACEPARENT = new AtomicReference<>();
    private static final HttpServer MEMBERSHIP = startMembershipStub();

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private Tracer tracer;

    @Autowired
    private GetMembershipPort getMembershipPort;

    @DynamicPropertySource
    static void membershipService(DynamicPropertyRegistry registry) {
        registry.add("service.membership.url", () -> "http://127.0.0.1:" + MEMBERSHIP.getAddress().getPort());
    }

    @AfterAll
    static void stopMembershipStub() {
        MEMBERSHIP.stop(0);
    }

    @Test
    void sendsTheCurrentTraceToTheServicesItCalls() {
        String traceId = Observation.createNotStarted("membership lookup", observationRegistry).observe(() -> {
            getMembershipPort.getMembership("1");
            return tracer.currentSpan().context().traceId();
        });

        assertThat(RECEIVED_TRACEPARENT.get()).startsWith("00-" + traceId + "-");
    }

    @Test
    void exportsApplicationLogsWithTheActiveTraceId() {
        String marker = UUID.randomUUID().toString();

        String traceId = Observation.createNotStarted("log probe", observationRegistry).observe(() -> {
            LoggerFactory.getLogger(ObservabilityTest.class).info("log probe {}", marker);
            return tracer.currentSpan().context().traceId();
        });

        assertThat(LOGS.getFinishedLogRecordItems())
                .filteredOn(log -> log.getBodyValue() != null && log.getBodyValue().asString().contains(marker))
                .singleElement()
                .extracting(LogRecordData::getSpanContext)
                .satisfies(spanContext -> assertThat(spanContext.getTraceId()).isEqualTo(traceId));
    }

    @Test
    void exposesConnectionPoolMetricsForPrometheus() throws Exception {
        HttpResponse<String> metrics = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/prometheus")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("hikaricp_connections_active");
    }

    private static HttpServer startMembershipStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/memberships/", exchange -> {
                RECEIVED_TRACEPARENT.set(exchange.getRequestHeaders().getFirst("traceparent"));
                byte[] body = "{\"membershipId\":\"1\",\"valid\":true}".getBytes(StandardCharsets.UTF_8);
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
    static class InMemoryLogs {

        @Bean
        LogRecordProcessor inMemoryLogRecordProcessor() {
            return SimpleLogRecordProcessor.create(LOGS);
        }
    }
}
