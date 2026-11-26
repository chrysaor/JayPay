package com.jaypay.payment.adapter.out.flow;

import com.jaypay.common.flow.FlowEventPublisher;
import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.payment.application.port.out.PaymentFlowPort;
import com.jaypay.payment.domain.NewPayment;
import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentRejectedException.Reason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
class PaymentFlowAdapter implements PaymentFlowPort {

    private static final Map<Reason, String> REFUSALS = Map.of(
            Reason.INVALID_PAYER, "The payer does not exist or is not valid",
            Reason.INVALID_MERCHANT, "The merchant does not exist, is not valid or is not a corporate member");

    private final FlowEventPublisher flowEvents;

    @Override
    public void refused(NewPayment payment, Reason reason) {
        flowEvents.publish(payment.requestId().value(), FlowStep.PAYMENT_REQUESTED, FlowOutcome.FAILED,
                REFUSALS.getOrDefault(reason, reason.name()), payment.requestMembershipId(), payment.requestPrice());
    }

    @Override
    public void requested(Payment payment) {
        publish(payment, FlowStep.PAYMENT_REQUESTED, FlowOutcome.SUCCEEDED, null);
    }

    @Override
    public void concluded(Payment payment) {
        switch (payment.paymentStatus()) {
            case PAID -> publish(payment, FlowStep.PAYER_DEBITED, FlowOutcome.SUCCEEDED, null);
            case FAILED -> publish(payment, FlowStep.PAYER_DEBITED, FlowOutcome.FAILED,
                    "money-service refused to take the price from the payer's wallet");
            case REQUESTED, SETTLED -> {
            }
        }
    }

    private void publish(Payment payment, FlowStep step, FlowOutcome outcome, String reason) {
        flowEvents.publish(payment.requestId().value(), step, outcome, reason, payment.requestMembershipId(), payment.requestPrice());
    }
}
