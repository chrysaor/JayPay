package com.jaypay.taskconsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.kafka.KafkaTestBroker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ActiveProfiles("test")
class TaskConsumerKafkaTest {

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Test
    void marksEverySubTaskSuccessfulAndPublishesTheResultUnderTheTaskId() throws Exception {
        KafkaTestBroker.send("jaypay.task.topic", "task-1", """
                {"taskID":"task-1","taskName":"Increase Money Task","membershipID":"7",
                 "subTaskList":[
                   {"membershipID":"7","subTaskName":"membership","taskType":"membership","status":"ready"},
                   {"membershipID":"7","subTaskName":"banking","taskType":"banking","status":"ready"}],
                 "toBankName":"jay","toBankAccountNumber":null,"moneyAmount":1000}
                """);

        ConsumerRecord<String, String> result =
                KafkaTestBroker.awaitRecord("jaypay.task.result.topic", "task-1", Duration.ofSeconds(60));

        JsonNode task = new ObjectMapper().readTree(result.value());
        assertThat(task.get("taskID").asText()).isEqualTo("task-1");
        assertThat(task.get("moneyAmount").asInt()).isEqualTo(1000);
        assertThat(task.get("subTaskList")).hasSize(2)
                .allSatisfy(subTask -> assertThat(subTask.get("status").asText()).isEqualTo("success"));
    }

    @Test
    void continuesTheTraceOfTheTaskOnTheResultRecord() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        KafkaTestBroker.send("jaypay.task.topic", "task-traced", """
                {"taskID":"task-traced","taskName":"Increase Money Task","membershipID":"7",
                 "subTaskList":[{"membershipID":"7","subTaskName":"banking","taskType":"banking","status":"ready"}],
                 "toBankName":"jay","toBankAccountNumber":null,"moneyAmount":1000}
                """, Map.of("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01"));

        ConsumerRecord<String, String> result =
                KafkaTestBroker.awaitRecord("jaypay.task.result.topic", "task-traced", Duration.ofSeconds(60));

        assertThat(KafkaTestBroker.header(result, "traceparent")).startsWith("00-" + traceId + "-");
    }
}
