package com.jaypay.eventflow.application.port.in;

import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.domain.Flow;
import com.jaypay.eventflow.domain.FlowStatus;
import com.jaypay.eventflow.domain.FlowSummary;

import java.util.List;
import java.util.Optional;

public interface FindFlowsUseCase {

    List<FlowSummary> recent(Optional<FlowType> flowType, Optional<FlowStatus> status);

    Optional<Flow> flow(FlowType flowType, String flowId);
}
