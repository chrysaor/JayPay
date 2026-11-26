package com.jaypay.eventflow.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.application.port.in.FindFlowsUseCase;
import com.jaypay.eventflow.application.port.in.RecordFlowStepUseCase;
import com.jaypay.eventflow.application.port.out.FlowStorePort;
import com.jaypay.eventflow.domain.Flow;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;
import com.jaypay.eventflow.domain.RecordedStep;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
class FlowService implements RecordFlowStepUseCase, FindFlowsUseCase {

    private static final int RECENT = 50;

    private final FlowStorePort flowStore;

    @Override
    @Transactional
    public void record(FlowType flowType, String flowId, RecordedStep step) {
        if (flowStore.addStepIfAbsent(flowType, flowId, step)) {
            flowStore.saveSummary(Flow.of(flowType, flowId, flowStore.steps(flowType, flowId)).summary());
        }
    }

    @Override
    public List<FlowSummary> recent(Optional<FlowStatus> status) {
        return flowStore.recent(status, RECENT);
    }

    @Override
    public Optional<Flow> flow(FlowType flowType, String flowId) {
        List<RecordedStep> steps = flowStore.steps(flowType, flowId);
        return steps.isEmpty() ? Optional.empty() : Optional.of(Flow.of(flowType, flowId, steps));
    }
}
