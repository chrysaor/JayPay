package com.jaypay.auth;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObservabilityTest {

    private static final InMemoryLogRecordExporter LOGS = InMemoryLogRecordExporter.create();

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private Tracer tracer;

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        StaffTokens.register(registry);
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

    @TestConfiguration
    static class InMemoryLogs {

        @Bean
        LogRecordProcessor inMemoryLogRecordProcessor() {
            return SimpleLogRecordProcessor.create(LOGS);
        }
    }
}
