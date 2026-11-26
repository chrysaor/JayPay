package com.jaypay.remittance.adapter.out.flow;

import com.jaypay.common.flow.FlowEventPublisher;
import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.remittance.application.port.out.RemittanceFlowPort;
import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceRejectedException.Reason;
import com.jaypay.remittance.domain.RemittanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.jaypay.remittance.domain.RemittanceStatus.COMPLETED;
import static com.jaypay.remittance.domain.RemittanceStatus.DEBITED;
import static com.jaypay.remittance.domain.RemittanceStatus.FAILED;
import static com.jaypay.remittance.domain.RemittanceStatus.REFUNDED;
import static com.jaypay.remittance.domain.RemittanceStatus.REFUNDING;
import static com.jaypay.remittance.domain.RemittanceStatus.REQUESTED;
import static com.jaypay.remittance.domain.RemittanceStatus.TRANSFERRING;

@Component
@RequiredArgsConstructor
class RemittanceFlowAdapter implements RemittanceFlowPort {

    private static final Map<Reason, String> REFUSALS = Map.of(
            Reason.INVALID_SENDER, "The sender does not exist or is not valid",
            Reason.INVALID_RECEIVER, "The receiver does not exist or is not valid",
            Reason.INVALID_BANK_ACCOUNT, "The bank did not confirm the receiving account");

    private static final Map<Transition, Report> REPORTS = Map.of(
            new Transition(REQUESTED, DEBITED), new Report(FlowStep.SENDER_DEBITED, FlowOutcome.SUCCEEDED, null),
            new Transition(REQUESTED, FAILED), new Report(FlowStep.SENDER_DEBITED, FlowOutcome.FAILED,
                    "money-service refused to take the amount from the sender's wallet"),
            new Transition(DEBITED, COMPLETED), new Report(FlowStep.RECEIVER_CREDITED, FlowOutcome.SUCCEEDED, null),
            new Transition(DEBITED, REFUNDING), new Report(FlowStep.RECEIVER_CREDITED, FlowOutcome.FAILED,
                    "money-service refused to add the amount to the receiver's wallet"),
            new Transition(TRANSFERRING, COMPLETED), new Report(FlowStep.BANK_TRANSFERRED, FlowOutcome.SUCCEEDED, null),
            new Transition(TRANSFERRING, REFUNDING), new Report(FlowStep.BANK_TRANSFERRED, FlowOutcome.FAILED,
                    "The bank refused the transfer"),
            new Transition(REFUNDING, REFUNDED), new Report(FlowStep.SENDER_REFUNDED, FlowOutcome.SUCCEEDED, null));

    private final FlowEventPublisher flowEvents;

    @Override
    public void refused(NewRemittance request, Reason reason) {
        publish(request, FlowStep.REMITTANCE_REQUESTED, FlowOutcome.FAILED, REFUSALS.getOrDefault(reason, reason.name()));
    }

    @Override
    public void requested(Remittance remittance) {
        publish(remittance.request(), FlowStep.REMITTANCE_REQUESTED, FlowOutcome.SUCCEEDED, null);
    }

    @Override
    public void advanced(Remittance remittance, RemittanceStatus next) {
        Report report = REPORTS.get(new Transition(remittance.status(), next));
        if (report != null) {
            publish(remittance.request(), report.step(), report.outcome(), report.reason());
        }
    }

    private void publish(NewRemittance request, FlowStep step, FlowOutcome outcome, String reason) {
        flowEvents.publish(request.requestId().value(), step, outcome, reason, request.fromMembershipId(), request.amount());
    }

    private record Transition(RemittanceStatus from, RemittanceStatus to) {
    }

    private record Report(FlowStep step, FlowOutcome outcome, String reason) {
    }
}
