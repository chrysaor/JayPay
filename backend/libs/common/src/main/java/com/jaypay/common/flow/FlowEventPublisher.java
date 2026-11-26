package com.jaypay.common.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Component
@Slf4j
public class FlowEventPublisher {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final String service;

    public FlowEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                              @Value("${flow.topic}") String topic,
                              @Value("${spring.application.name}") String service) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.service = service;
    }

    public void publish(String flowId, FlowStep step, FlowOutcome outcome, String reason, String membershipId, Long amount) {
        FlowStepEvent event = new FlowStepEvent(step.flowType(), flowId, step, service, outcome, reason, Instant.now(), membershipId, amount);
        String value;
        try {
            value = JSON.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Could not write step {} of flow {}: {}", step, flowId, e.getMessage());
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(flowId, step, value);
                }
            });
        } else {
            send(flowId, step, value);
        }
    }

    private void send(String flowId, FlowStep step, String value) {
        try {
            kafkaTemplate.send(topic, flowId, value).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.warn("Could not publish step {} of flow {}: {}", step, flowId, exception.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not publish step {} of flow {}: {}", step, flowId, e.getMessage());
        }
    }
}
