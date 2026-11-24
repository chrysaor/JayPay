package com.jaypay.loggingconsumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LoggingConsumer {

    @KafkaListener(topics = "${logging.topic}", groupId = "my-group")
    public void consume(String message) {
        System.out.println("Received message: " + message);
    }
}
