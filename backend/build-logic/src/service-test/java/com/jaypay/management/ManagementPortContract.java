package com.jaypay.management;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.metrics.stats.CumulativeCount;
import org.apache.kafka.common.metrics.stats.CumulativeSum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ManagementPortContract {

    private static final String UNMAPPED = "/no-such-path";

    private static final String NEGATIVE_REBALANCE_CLIENT = "consumer-negative-rebalance-1";

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MeterRegistry meterRegistry;

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

    @Test
    void servesPrometheusMetricsWhileTheKafkaRebalanceLatencyTotalIsNegative() throws Exception {
        try (Metrics kafkaMetrics = new Metrics();
             KafkaClientMetrics binder = new KafkaClientMetrics(consumerReporting(kafkaMetrics),
                     Tags.of("spring.id", "negativeRebalanceConsumerFactory"))) {
            Map<String, String> clientId = Map.of("client-id", NEGATIVE_REBALANCE_CLIENT);
            Sensor rebalanceLatency = kafkaMetrics.sensor("rebalance-latency");
            rebalanceLatency.add(kafkaMetrics.metricName("rebalance-latency-total", "consumer-coordinator-metrics", clientId),
                    new CumulativeSum());
            rebalanceLatency.add(kafkaMetrics.metricName("rebalance-total", "consumer-coordinator-metrics", clientId),
                    new CumulativeCount());
            rebalanceLatency.record(-745);
            binder.bindTo(meterRegistry);

            HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .contains("jvm_memory_used_bytes")
                    .contains("kafka_consumer_coordinator_rebalance_total{client_id=\"" + NEGATIVE_REBALANCE_CLIENT + "\"")
                    .doesNotContain("kafka_consumer_coordinator_rebalance_latency_total");
        }
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

    private static MockConsumer<String, String> consumerReporting(Metrics kafkaMetrics) {
        return new MockConsumer<>("earliest") {
            @Override
            public synchronized Map<MetricName, ? extends Metric> metrics() {
                return kafkaMetrics.metrics();
            }
        };
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
