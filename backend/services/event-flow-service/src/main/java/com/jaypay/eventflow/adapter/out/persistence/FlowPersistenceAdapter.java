package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.application.port.out.FlowStorePort;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;
import com.jaypay.eventflow.domain.RecordedStep;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@PersistenceAdapter
@RequiredArgsConstructor
class FlowPersistenceAdapter implements FlowStorePort {

    private final SpringDataFlowStepRepository steps;
    private final SpringDataFlowRepository flows;

    @Override
    public boolean addStepIfAbsent(FlowType flowType, String flowId, RecordedStep step) {
        if (steps.existsByFlowTypeAndFlowIdAndStep(flowType.name(), flowId, step.step().name())) {
            return false;
        }
        steps.saveAndFlush(new FlowStepJpaEntity(null, flowType.name(), flowId, step.step().name(), step.service(), step.outcome(),
                step.reason(), step.occurredAt(), step.membershipId(), step.amount()));
        return true;
    }

    @Override
    public List<RecordedStep> steps(FlowType flowType, String flowId) {
        return steps.findByFlowTypeAndFlowId(flowType.name(), flowId).stream()
                .map(step -> new RecordedStep(FlowStep.valueOf(step.getStep()), step.getService(), step.getOutcome(), step.getReason(),
                        step.getOccurredAt(), step.getMembershipId(), step.getAmount()))
                .toList();
    }

    @Override
    public void saveSummary(FlowSummary summary) {
        flows.save(new FlowJpaEntity(summary.flowType() + "/" + summary.flowId(), summary.flowType().name(), summary.flowId(),
                summary.status(), summary.membershipId(), summary.amount(), summary.startedAt(), summary.updatedAt(),
                summary.lastStep().name(), name(summary.failedStep()), summary.stepCount()));
    }

    @Override
    public List<FlowSummary> recent(Optional<FlowType> flowType, Optional<FlowStatus> status, int limit) {
        return flows.findRecent(flowType.map(FlowType::name).orElse(null), status.orElse(null), Limit.of(limit)).stream()
                .map(flow -> new FlowSummary(FlowType.valueOf(flow.getFlowType()), flow.getFlowId(), flow.getStatus(), flow.getMembershipId(),
                        flow.getAmount(), flow.getStartedAt(), flow.getUpdatedAt(), FlowStep.valueOf(flow.getLastStep()), step(flow.getFailedStep()),
                        flow.getStepCount()))
                .toList();
    }

    @Override
    public int forgetFlowsUpdatedBefore(Instant cutoff) {
        steps.deleteStepsOfFlowsUpdatedBefore(cutoff);
        return flows.deleteUpdatedBefore(cutoff);
    }

    private static String name(FlowStep step) {
        return step == null ? null : step.name();
    }

    private static FlowStep step(String name) {
        return name == null ? null : FlowStep.valueOf(name);
    }
}
