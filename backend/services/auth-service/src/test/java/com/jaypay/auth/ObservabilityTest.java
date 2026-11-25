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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObservabilityTest {

    private static final InMemoryLogRecordExporter LOGS = InMemoryLogRecordExporter.create();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private Tracer tracer;

    @Test
    void startsAndReportsHealthyWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
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
    void exposesJvmAndConnectionPoolMetricsForPrometheusWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("hikaricp_connections_active")));
    }

    @Test
    void keepsEveryOtherActuatorEndpointPrivate() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class InMemoryLogs {

        @Bean
        LogRecordProcessor inMemoryLogRecordProcessor() {
            return SimpleLogRecordProcessor.create(LOGS);
        }
    }
}
