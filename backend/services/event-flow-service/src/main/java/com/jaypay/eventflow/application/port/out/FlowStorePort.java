package com.jaypay.eventflow.application.port.out;

import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;
import com.jaypay.eventflow.domain.RecordedStep;

import java.util.List;
import java.util.Optional;

public interface FlowStorePort {

    boolean addStepIfAbsent(FlowType flowType, String flowId, RecordedStep step);

    List<RecordedStep> steps(FlowType flowType, String flowId);

    void saveSummary(FlowSummary summary);

    List<FlowSummary> recent(Optional<FlowStatus> status, int limit);
}
