package com.jaypay.eventflow.domain;

import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;

import java.time.Instant;

public record FlowSummary(
        FlowType flowType,
        String flowId,
        FlowStatus status,
        String membershipId,
        Long amount,
        Instant startedAt,
        Instant updatedAt,
        FlowStep lastStep,
        FlowStep failedStep,
        int stepCount) {
}
