package com.jaypay.money.adapter.out.flow;

import com.jaypay.common.flow.FlowEventPublisher;
import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.money.application.port.out.RechargeFlowPort;
import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.RechargeFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RechargeFlowAdapter implements RechargeFlowPort {

    private final FlowEventPublisher flowEvents;

    @Override
    public void requested(MoneyChangingRequest request) {
        publish(request, FlowStep.RECHARGE_REQUESTED, FlowOutcome.SUCCEEDED, null);
    }

    @Override
    public void requestFailed(MoneyChangingRequest request, RechargeFailure failure) {
        publish(request, FlowStep.RECHARGE_REQUESTED, FlowOutcome.FAILED, failure.reason());
    }

    @Override
    public void walletIncreased(MoneyChangingRequest request) {
        publish(request, FlowStep.WALLET_INCREASED, FlowOutcome.SUCCEEDED, null);
    }

    @Override
    public void failed(MoneyChangingRequest request, RechargeFailure failure) {
        publish(request, FlowStep.RECHARGE_FAILED, FlowOutcome.FAILED, failure.reason());
    }

    private void publish(MoneyChangingRequest request, FlowStep step, FlowOutcome outcome, String reason) {
        flowEvents.publish(request.requestId().value(), step, outcome, reason, request.targetMembershipId(), request.amount().value());
    }
}
