package com.jaypay.eventflow.application.port.in;

import com.jaypay.common.flow.FlowType;
import com.jaypay.eventflow.domain.RecordedStep;

public interface RecordFlowStepUseCase {

    void record(FlowType flowType, String flowId, RecordedStep step);
}
