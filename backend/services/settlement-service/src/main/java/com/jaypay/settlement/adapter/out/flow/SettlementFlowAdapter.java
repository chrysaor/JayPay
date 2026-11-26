package com.jaypay.settlement.adapter.out.flow;

import com.jaypay.common.flow.FlowEventPublisher;
import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.Payout;
import com.jaypay.settlement.application.port.out.SettlementFlowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SettlementFlowAdapter implements SettlementFlowPort {

    private final FlowEventPublisher flowEvents;

    @Override
    public void waitingForAccount(Payment payment) {
        publish(payment, FlowStep.SETTLEMENT_STARTED, FlowOutcome.IN_PROGRESS,
                "The merchant has no registered bank account, so the payment waits for a later run");
    }

    @Override
    public void started(Payment payment) {
        publish(payment, FlowStep.SETTLEMENT_STARTED, FlowOutcome.SUCCEEDED, null);
    }

    @Override
    public void paidOut(Payment payment, Payout payout) {
        switch (payout) {
            case PAID -> publish(payment, FlowStep.MERCHANT_PAID_OUT, FlowOutcome.SUCCEEDED, null);
            case REFUSED -> publish(payment, FlowStep.MERCHANT_PAID_OUT, FlowOutcome.FAILED, "The bank refused the payout to the merchant");
            case UNCONFIRMED -> {
            }
        }
    }

    @Override
    public void settled(Payment payment) {
        publish(payment, FlowStep.PAYMENT_SETTLED, FlowOutcome.SUCCEEDED, null);
    }

    private void publish(Payment payment, FlowStep step, FlowOutcome outcome, String reason) {
        flowEvents.publish(payment.settlementId(), step, outcome, reason, payment.franchiseId(), payment.payout());
    }
}
