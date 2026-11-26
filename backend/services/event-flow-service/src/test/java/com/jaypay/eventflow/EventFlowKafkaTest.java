package com.jaypay.eventflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventFlowKafkaTest {

    private static final String TOPIC = "jaypay.flow.events";
    private static final Instant START = Instant.parse("2026-11-26T01:00:00Z");

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Test
    void collectsTheStepsOfARechargeIntoOneTimelineInTheOrderTheyHappened() throws Exception {
        String flowId = UUID.randomUUID().toString();

        send(flowId, "BANK_CHARGED", "banking-service", "SUCCEEDED", null, 30);
        send(flowId, "RECHARGE_REQUESTED", "money-service", "SUCCEEDED", null, 0);
        send(flowId, "MEMBERSHIP_CHECKED", "membership-service", "SUCCEEDED", null, 10);
        send(flowId, "BANK_ACCOUNT_CHECKED", "banking-service", "SUCCEEDED", null, 20);
        send(flowId, "WALLET_INCREASED", "money-service", "SUCCEEDED", null, 40);

        JsonNode flow = awaitFlow(flowId, 5);

        assertThat(flow.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(flow.get("membershipId").asText()).isEqualTo("12");
        assertThat(flow.get("amount").asLong()).isEqualTo(3000);
        assertThat(flow.get("lastStep").asText()).isEqualTo("WALLET_INCREASED");
        assertThat(flow.get("failedStep").isNull()).isTrue();
        assertThat(flow.get("startedAt").asText()).isEqualTo(START.toString());
        assertThat(steps(flow)).containsExactly(
                "RECHARGE_REQUESTED", "MEMBERSHIP_CHECKED", "BANK_ACCOUNT_CHECKED", "BANK_CHARGED", "WALLET_INCREASED");
        assertThat(flow.get("steps").get(1).get("service").asText()).isEqualTo("membership-service");
    }

    @Test
    void keepsTheFirstCopyOfAStepThatKafkaDeliversTwice() throws Exception {
        String flowId = UUID.randomUUID().toString();

        send(flowId, "RECHARGE_REQUESTED", "money-service", "SUCCEEDED", null, 0);
        send(flowId, "MEMBERSHIP_CHECKED", "membership-service", "FAILED", "The member does not exist or is not valid", 10);
        send(flowId, "MEMBERSHIP_CHECKED", "membership-service", "SUCCEEDED", null, 15);
        send(flowId, "RECHARGE_FAILED", "money-service", "FAILED", "membership-service did not confirm the member", 20);

        JsonNode flow = awaitFlow(flowId, 3);

        assertThat(flow.get("status").asText()).isEqualTo("FAILED");
        assertThat(flow.get("failedStep").asText()).isEqualTo("MEMBERSHIP_CHECKED");
        assertThat(flow.get("stepCount").asInt()).isEqualTo(3);
        assertThat(flow.get("steps").get(1).get("reason").asText()).isEqualTo("The member does not exist or is not valid");
    }

    @Test
    void skipsRecordsThatAreNotFlowStepsAndKeepsReadingTheTopic() throws Exception {
        String flowId = UUID.randomUUID().toString();

        KafkaTestBroker.send(TOPIC, flowId, "not json");
        KafkaTestBroker.send(TOPIC, flowId, "{\"flowType\":\"RECHARGE\",\"flowId\":\"" + flowId + "\",\"step\":\"NO_SUCH_STEP\"}");
        KafkaTestBroker.send(TOPIC, flowId, "{\"flowType\":\"RECHARGE\",\"flowId\":\"" + flowId + "\",\"step\":\"BANK_CHARGED\"}");
        send(flowId, "RECHARGE_REQUESTED", "money-service", "SUCCEEDED", null, 0);

        JsonNode flow = awaitFlow(flowId, 1);

        assertThat(flow.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(steps(flow)).containsExactly("RECHARGE_REQUESTED");
    }

    @Test
    void listsTheNewestFlowsFirstAndFiltersThemByStatus() throws Exception {
        String older = UUID.randomUUID().toString();
        String newer = UUID.randomUUID().toString();

        send(older, "RECHARGE_REQUESTED", "money-service", "SUCCEEDED", null, 100_000);
        send(newer, "RECHARGE_REQUESTED", "money-service", "FAILED", "The member has no registered bank account", 200_000);
        awaitFlow(older, 1);
        awaitFlow(newer, 1);

        List<String> all = flowIds(get("/flows").body());
        assertThat(all).containsSubsequence(newer, older);

        List<String> failed = flowIds(get("/flows?status=FAILED").body());
        assertThat(failed).contains(newer).doesNotContain(older);

        JsonNode first = json.readTree(get("/flows").body()).get(0);
        assertThat(first.has("steps")).isFalse();
        assertThat(first.get("stepCount").asInt()).isPositive();
    }

    @Test
    void answersNotFoundForAFlowItNeverSaw() throws Exception {
        HttpResponse<String> response = get("/flows/RECHARGE/" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(json.readTree(response.body()).get("error").asText()).isEqualTo("FLOW_NOT_FOUND");
        assertThat(get("/flows/NO_SUCH_TYPE/x").statusCode()).isEqualTo(404);
    }

    @Test
    void refusesAStatusItDoesNotKnow() throws Exception {
        HttpResponse<String> response = get("/flows?status=LOST");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("error").asText()).isEqualTo("INVALID_STATUS");
    }

    private void send(String flowId, String step, String service, String outcome, String reason, long millis) throws Exception {
        String record = json.writeValueAsString(json.createObjectNode()
                .put("flowType", "RECHARGE")
                .put("flowId", flowId)
                .put("step", step)
                .put("service", service)
                .put("outcome", outcome)
                .put("reason", reason)
                .put("occurredAt", START.plusMillis(millis).toString())
                .put("membershipId", "12")
                .put("amount", 3000));
        KafkaTestBroker.send(TOPIC, flowId, record);
    }

    private JsonNode awaitFlow(String flowId, int stepCount) {
        JsonNode[] found = new JsonNode[1];
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            HttpResponse<String> response = get("/flows/RECHARGE/" + flowId);
            assertThat(response.statusCode()).isEqualTo(200);
            found[0] = json.readTree(response.body());
            assertThat(found[0].get("stepCount").asInt()).isEqualTo(stepCount);
        });
        return found[0];
    }

    private static List<String> steps(JsonNode flow) {
        List<String> steps = new ArrayList<>();
        flow.get("steps").forEach(step -> steps.add(step.get("step").asText()));
        return steps;
    }

    private List<String> flowIds(String body) throws Exception {
        List<String> ids = new ArrayList<>();
        json.readTree(body).forEach(flow -> ids.add(flow.get("flowId").asText()));
        return ids;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
