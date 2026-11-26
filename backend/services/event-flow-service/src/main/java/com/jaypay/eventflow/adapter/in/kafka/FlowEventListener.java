package com.jaypay.eventflow.adapter.in.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jaypay.common.flow.FlowStepEvent;
import com.jaypay.eventflow.application.port.in.RecordFlowStepUseCase;
import com.jaypay.eventflow.domain.RecordedStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JacksonException;

@Component
@RequiredArgsConstructor
@Slf4j
class FlowEventListener {

    private static final int LONGEST_ID = 255;
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final RecordFlowStepUseCase recordFlowStepUseCase;

    @KafkaListener(topics = "${flow.topic}", groupId = "event-flow-service")
    public void consume(String message) {
        FlowStepEvent event;
        try {
            event = JSON.readValue(message, FlowStepEvent.class);
        } catch (JacksonException e) {
            log.warn("Skipped a flow record that is not a flow step: {}", e.getOriginalMessage());
            return;
        }
        if (!wellFormed(event)) {
            log.warn("Skipped a flow step without a flow, a step of that flow, a service, an outcome or a time: {} {} {}",
                    event.flowType(), event.step(), event.service());
            return;
        }
        recordFlowStepUseCase.record(event.flowType(), event.flowId(), new RecordedStep(
                event.step(), event.service(), event.outcome(), event.reason(), event.occurredAt(), event.membershipId(), event.amount()));
    }

    private static boolean wellFormed(FlowStepEvent event) {
        return event.flowType() != null && event.step() != null && event.step().flowType() == event.flowType()
                && present(event.flowId()) && present(event.service()) && event.outcome() != null && event.occurredAt() != null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && value.length() <= LONGEST_ID;
    }
}
