package com.jaypay.taskconsumer;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TaskResultProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskResultProducer(KafkaTemplate<String, String> kafkaTemplate, @Value("${task.result.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendTaskResult(String key, Object task) {
        String jsonStringToProduce;
        try {
            jsonStringToProduce = objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        kafkaTemplate.send(topic, key, jsonStringToProduce).whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("[TaskResultProducer] Message sent successfully. Offset: {}", result.getRecordMetadata().offset());
            } else {
                log.warn("[TaskResultProducer] Failed to send message: {}", exception.getMessage());
            }
        });
    }
}
