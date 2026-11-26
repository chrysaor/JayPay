package com.jaypay.eventflow.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.application.port.in.FindFlowsUseCase;
import com.jaypay.eventflow.application.port.in.ForgetOldFlowsUseCase;
import com.jaypay.eventflow.application.port.in.RecordFlowStepUseCase;
import com.jaypay.eventflow.application.port.out.FlowStorePort;
import com.jaypay.eventflow.domain.Flow;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;
import com.jaypay.eventflow.domain.RecordedStep;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@UseCase
class FlowService implements RecordFlowStepUseCase, FindFlowsUseCase, ForgetOldFlowsUseCase {

    private static final int RECENT = 50;

    private final FlowStorePort flowStore;
    private final Duration retention;

    FlowService(FlowStorePort flowStore, @Value("${flow.retention.period}") Duration retention) {
        this.flowStore = flowStore;
        this.retention = retention;
    }

    @Override
    @Transactional
    public void record(FlowType flowType, String flowId, RecordedStep step) {
        if (flowStore.addStepIfAbsent(flowType, flowId, step)) {
            flowStore.saveSummary(Flow.of(flowType, flowId, flowStore.steps(flowType, flowId)).summary());
        }
    }

    @Override
    public List<FlowSummary> recent(Optional<FlowType> flowType, Optional<FlowStatus> status) {
        return flowStore.recent(flowType, status, RECENT);
    }

    @Override
    public Optional<Flow> flow(FlowType flowType, String flowId) {
        List<RecordedStep> steps = flowStore.steps(flowType, flowId);
        return steps.isEmpty() ? Optional.empty() : Optional.of(Flow.of(flowType, flowId, steps));
    }

    @Override
    @Transactional
    public int forgetOldFlows() {
        return flowStore.forgetFlowsUpdatedBefore(Instant.now().minus(retention));
    }
}
