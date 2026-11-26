package com.jaypay.eventflow.domain;

import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;

import java.time.Instant;

public record RecordedStep(
        FlowStep step,
        String service,
        FlowOutcome outcome,
        String reason,
        Instant occurredAt,
        String membershipId,
        Long amount) {
}
