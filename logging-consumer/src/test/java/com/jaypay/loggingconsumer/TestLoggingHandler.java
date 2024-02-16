package com.jaypay.loggingconsumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

@SpringBootTest
public class TestLoggingHandler {

    @InjectMocks
    private LoggingHandler loggingHandler;

    @Mock
    private KafkaConsumer<String, String> loggingConsumer;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        loggingHandler = new LoggingHandler(loggingConsumer);
    }

    private static Stream<String> provideStringForConsumer() {
        return Stream.of(
                "[log] hello world",
                "[logging] hello world"
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringForConsumer")
    public void testConsumeHandler(String consumedString) {
        // Mocking Kafka Consumer
        when(loggingConsumer.poll(any(Duration.class)))
                .thenReturn(createMockRecordsFromString(consumedString));

        loggingHandler.consume();

        verify(loggingConsumer, atLeastOnce()).poll(any(Duration.class));
    }

    private ConsumerRecords<String, String> createMockRecordsFromString(String value) {
        List<ConsumerRecord<String, String>> list = new ArrayList<>();
        ConsumerRecord<String, String> dummyRecord
                = new ConsumerRecord<>("test", 0, 0, "key", value);
        list.add(dummyRecord);

        Map<TopicPartition, List<ConsumerRecord<String, String>>> map = new HashMap<>();
        map.put(new TopicPartition("test", 0), list);
        return new ConsumerRecords<>(map);
    }
}
