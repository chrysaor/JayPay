package com.jaypay.eventflow.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.application.port.out.FlowStorePort;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;
import com.jaypay.eventflow.domain.RecordedStep;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.Optional;

@PersistenceAdapter
@RequiredArgsConstructor
class FlowPersistenceAdapter implements FlowStorePort {

    private final SpringDataFlowStepRepository steps;
    private final SpringDataFlowRepository flows;

    @Override
    public boolean addStepIfAbsent(FlowType flowType, String flowId, RecordedStep step) {
        if (steps.existsByFlowTypeAndFlowIdAndStep(flowType, flowId, step.step())) {
            return false;
        }
        steps.saveAndFlush(new FlowStepJpaEntity(null, flowType, flowId, step.step(), step.service(), step.outcome(),
                step.reason(), step.occurredAt(), step.membershipId(), step.amount()));
        return true;
    }

    @Override
    public List<RecordedStep> steps(FlowType flowType, String flowId) {
        return steps.findByFlowTypeAndFlowId(flowType, flowId).stream()
                .map(step -> new RecordedStep(step.getStep(), step.getService(), step.getOutcome(), step.getReason(),
                        step.getOccurredAt(), step.getMembershipId(), step.getAmount()))
                .toList();
    }

    @Override
    public void saveSummary(FlowSummary summary) {
        flows.save(new FlowJpaEntity(summary.flowType() + "/" + summary.flowId(), summary.flowType(), summary.flowId(),
                summary.status(), summary.membershipId(), summary.amount(), summary.startedAt(), summary.updatedAt(),
                summary.lastStep(), summary.failedStep(), summary.stepCount()));
    }

    @Override
    public List<FlowSummary> recent(Optional<FlowStatus> status, int limit) {
        List<FlowJpaEntity> found = status
                .map(wanted -> flows.findByStatusOrderByStartedAtDesc(wanted, Limit.of(limit)))
                .orElseGet(() -> flows.findByOrderByStartedAtDesc(Limit.of(limit)));
        return found.stream()
                .map(flow -> new FlowSummary(flow.getFlowType(), flow.getFlowId(), flow.getStatus(), flow.getMembershipId(),
                        flow.getAmount(), flow.getStartedAt(), flow.getUpdatedAt(), flow.getLastStep(), flow.getFailedStep(),
                        flow.getStepCount()))
                .toList();
    }
}
