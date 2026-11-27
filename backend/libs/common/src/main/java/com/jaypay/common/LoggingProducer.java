package com.jaypay.common;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
                    log.debug("Sent the logging record {} at offset {}", key, result.getRecordMetadata().offset());
                } else {
                    log.warn("Could not send the logging record {}", key, exception);
                }
            });
        } catch (KafkaException e) {
            log.warn("Could not send the logging record {}", key, e);
        }
    }

}
