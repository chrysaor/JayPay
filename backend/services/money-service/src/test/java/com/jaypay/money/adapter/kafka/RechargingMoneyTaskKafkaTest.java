package com.jaypay.money.adapter.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.CountDownLatchManager;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.common.SubTask;
import com.jaypay.common.kafka.KafkaTestBroker;
import com.jaypay.money.application.port.out.SendRechargingMoneyTaskPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ActiveProfiles("test")
class RechargingMoneyTaskKafkaTest {

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Autowired
    private SendRechargingMoneyTaskPort sendRechargingMoneyTaskPort;

    @Autowired
    private CountDownLatchManager countDownLatchManager;

    @Test
    void publishesTheTaskAsJsonUnderItsId() throws Exception {
        String taskId = UUID.randomUUID().toString();

        sendRechargingMoneyTaskPort.sendRechargingMoneyTaskPort(task(taskId, "ready"));

        ConsumerRecord<String, String> record =
                KafkaTestBroker.awaitRecord("jaypay.task.topic", taskId, Duration.ofSeconds(60));
        JsonNode published = new ObjectMapper().readTree(record.value());
        assertThat(published.get("taskID").asText()).isEqualTo(taskId);
        assertThat(published.get("membershipID").asText()).isEqualTo("7");
        assertThat(published.get("moneyAmount").asInt()).isEqualTo(1000);
        assertThat(published.get("subTaskList").get(1).get("status").asText()).isEqualTo("ready");
    }

    @Test
    void releasesTheWaitingRequestWithSuccessWhenEverySubTaskSucceeded() throws Exception {
        assertThat(resultFor("success")).isEqualTo("success");
    }

    @Test
    void releasesTheWaitingRequestWithFailedWhenASubTaskFailed() throws Exception {
        assertThat(resultFor("fail")).isEqualTo("failed");
    }

    @Test
    void reportsTheTaskOutcomeOnTheLoggingTopic() throws Exception {
        String taskId = UUID.randomUUID().toString();
        countDownLatchManager.addCountDownLatch(taskId);

        KafkaTestBroker.send("jaypay.task.result.topic", taskId,
                new ObjectMapper().writeValueAsString(task(taskId, "success")));

        ConsumerRecord<String, String> record =
                KafkaTestBroker.awaitRecord("jaypay.logging.out.stdout", taskId, Duration.ofSeconds(60));
        assertThat(record.value()).isEqualTo("task success");
    }

    private String resultFor(String subTaskStatus) throws Exception {
        String taskId = UUID.randomUUID().toString();
        countDownLatchManager.addCountDownLatch(taskId);

        KafkaTestBroker.send("jaypay.task.result.topic", taskId,
                new ObjectMapper().writeValueAsString(task(taskId, subTaskStatus)));

        assertThat(countDownLatchManager.getCountDownLatch(taskId).await(60, TimeUnit.SECONDS)).isTrue();
        return countDownLatchManager.getDataForKey(taskId);
    }

    private static RechargingMoneyTask task(String taskId, String status) {
        return RechargingMoneyTask.builder()
                .taskID(taskId)
                .taskName("Increase Money Task")
                .membershipID("7")
                .subTaskList(List.of(
                        new SubTask("7", "membership", "membership", "success"),
                        new SubTask("7", "banking", "banking", status)))
                .toBankName("jay")
                .moneyAmount(1000)
                .build();
    }
}
