package com.jaypay.loggingconsumer;

import com.jaypay.common.kafka.KafkaTestBroker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LoggingConsumerKafkaTest {

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestBroker::bootstrapServers);
    }

    @Test
    void logsEveryMessageFromTheLoggingTopic(CapturedOutput output) {
        String message = "Before executing method: " + UUID.randomUUID();

        KafkaTestBroker.send("jaypay.logging.out.stdout", "logging", message);

        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(output.getOut()).contains("Received message: " + message));
    }
}
