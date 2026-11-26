package com.jaypay.eventflow.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.application.port.in.FindFlowsUseCase;
import com.jaypay.eventflow.domain.Flow;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;
import com.jaypay.eventflow.domain.RecordedStep;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@WebAdapter
@RestController
@RequiredArgsConstructor
class FlowController {

    private final FindFlowsUseCase findFlowsUseCase;

    @GetMapping("/flows")
    List<FlowView> recent(@RequestParam Optional<String> status) {
        return findFlowsUseCase.recent(status.map(FlowController::status)).stream().map(FlowView::of).toList();
    }

    @GetMapping("/flows/{flowType}/{flowId}")
    TimelineView timeline(@PathVariable String flowType, @PathVariable String flowId) {
        return Arrays.stream(FlowType.values()).filter(type -> type.name().equals(flowType)).findFirst()
                .flatMap(type -> findFlowsUseCase.flow(type, flowId))
                .map(TimelineView::of)
                .orElseThrow(FlowNotFound::new);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Error notFound(FlowNotFound notFound) {
        return new Error("FLOW_NOT_FOUND", "No flow has this type and id.");
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Error invalidStatus(InvalidStatus invalid) {
        return new Error("INVALID_STATUS", "Ask for the status IN_PROGRESS, SUCCEEDED or FAILED, or for none.");
    }

    private static FlowStatus status(String value) {
        return Arrays.stream(FlowStatus.values()).filter(status -> status.name().equals(value)).findFirst().orElseThrow(InvalidStatus::new);
    }

    private static String time(Instant instant) {
        return instant.toString();
    }

    record Error(String error, String message) {
    }

    record FlowView(String flowType, String flowId, String status, String membershipId, Long amount, String startedAt,
                    String updatedAt, String lastStep, String failedStep, int stepCount) {
        static FlowView of(FlowSummary flow) {
            return new FlowView(flow.flowType().name(), flow.flowId(), flow.status().name(), flow.membershipId(), flow.amount(),
                    time(flow.startedAt()), time(flow.updatedAt()), flow.lastStep().name(),
                    flow.failedStep() == null ? null : flow.failedStep().name(), flow.stepCount());
        }
    }

    record StepView(String step, String service, String outcome, String reason, String occurredAt) {
        static StepView of(RecordedStep step) {
            return new StepView(step.step().name(), step.service(), step.outcome().name(), step.reason(), time(step.occurredAt()));
        }
    }

    record TimelineView(String flowType, String flowId, String status, String membershipId, Long amount, String startedAt,
                        String updatedAt, String lastStep, String failedStep, int stepCount, List<StepView> steps) {
        static TimelineView of(Flow flow) {
            FlowView summary = FlowView.of(flow.summary());
            return new TimelineView(summary.flowType(), summary.flowId(), summary.status(), summary.membershipId(), summary.amount(),
                    summary.startedAt(), summary.updatedAt(), summary.lastStep(), summary.failedStep(), summary.stepCount(),
                    flow.steps().stream().map(StepView::of).toList());
        }
    }

    static class FlowNotFound extends RuntimeException {
    }

    static class InvalidStatus extends RuntimeException {
    }
}
