package com.jaypay.common.flow;

import java.time.Instant;

public record FlowStepEvent(
        FlowType flowType,
        String flowId,
        FlowStep step,
        String service,
        FlowOutcome outcome,
        String reason,
        Instant occurredAt,
        String membershipId,
        Long amount) {
}
