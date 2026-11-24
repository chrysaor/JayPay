package com.jaypay.money.adapter.out.kafka;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.money.application.port.out.SendRechargingMoneyTaskPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskProducer implements SendRechargingMoneyTaskPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper();

    public TaskProducer(KafkaTemplate<String, String> kafkaTemplate, @Value("${task.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void sendRechargingMoneyTaskPort(RechargingMoneyTask task) {
        this.sendMessage(task.getTaskID(), task);
    }

    public void sendMessage(String key, RechargingMoneyTask value) {
        String jsonStringToProduce;
        try {
            jsonStringToProduce = mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        kafkaTemplate.send(topic, key, jsonStringToProduce).whenComplete((result, exception) -> {
            if (exception == null) {
                System.out.println("Message sent successfully. Offset: " + result.getRecordMetadata().offset());
            } else {
                System.err.println("Failed to send message: " + exception.getMessage());
            }
        });
    }

}
