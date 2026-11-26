package com.jaypay.eventflow.domain;

import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record Flow(FlowSummary summary, List<RecordedStep> steps) {

    private static final Comparator<RecordedStep> IN_ORDER =
            Comparator.comparing(RecordedStep::occurredAt).thenComparing(RecordedStep::step);

    public static Flow of(FlowType flowType, String flowId, List<RecordedStep> recorded) {
        List<RecordedStep> steps = recorded.stream().sorted(IN_ORDER).toList();
        FlowStep failedStep = steps.stream().filter(step -> step.outcome() == FlowOutcome.FAILED)
                .map(RecordedStep::step).findFirst().orElse(null);
        boolean completed = steps.stream().anyMatch(step -> step.step().completesFlow() && step.outcome() == FlowOutcome.SUCCEEDED);
        FlowStatus status = failedStep != null ? FlowStatus.FAILED : completed ? FlowStatus.SUCCEEDED : FlowStatus.IN_PROGRESS;
        return new Flow(new FlowSummary(flowType, flowId, status,
                first(steps, RecordedStep::membershipId),
                first(steps, RecordedStep::amount),
                steps.getFirst().occurredAt(),
                steps.getLast().occurredAt(),
                steps.getLast().step(),
                failedStep,
                steps.size()), steps);
    }

    private static <T> T first(List<RecordedStep> steps, Function<RecordedStep, T> field) {
        return steps.stream().map(field).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
