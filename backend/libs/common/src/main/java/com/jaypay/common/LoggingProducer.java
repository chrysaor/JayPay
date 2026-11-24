package com.jaypay.common;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoggingProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public LoggingProducer(KafkaTemplate<String, String> kafkaTemplate, @Value("${logging.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendMessage(String key, String value) {
        try {
            kafkaTemplate.send(topic, key, value).whenComplete((result, exception) -> {
                if (exception == null) {
                    System.out.println("Message sent successfully. Offset: " + result.getRecordMetadata().offset());
                } else {
                    System.err.println("Failed to send message: " + exception.getMessage());
                }
            });
        } catch (KafkaException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

}
