package com.jaypay.loggingconsumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoggingConsumer {

    @KafkaListener(topics = "${logging.topic}", groupId = "my-group")
    public void consume(String message) {
        log.info("Received message: {}", message);
    }
}
