package com.jaypay.banking.adapter.in.axon;

import com.jaypay.banking.application.port.in.TransferMoneyUseCase;
import com.jaypay.banking.domain.CompanyAccount;
import com.jaypay.banking.domain.RecordedTransfer;
import com.jaypay.banking.domain.TransferStatus;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingRefusedEvent;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import com.jaypay.common.event.RollbackFirmBankingUnconfirmedEvent;
import com.jaypay.common.flow.FlowEventPublisher;
import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
@NoArgsConstructor
@Slf4j
public class FirmBankingRequestAggregate {

    @AggregateIdentifier
    private String id;

    @CommandHandler
    public FirmBankingRequestAggregate(RequestFirmBankingCommand command, TransferMoneyUseCase transferMoneyUseCase,
                                       CompanyAccount companyAccount, FlowEventPublisher flowEvents) {
        id = command.getAggregateIdentifier();
        RecordedTransfer charge = transferMoneyUseCase.transfer(companyAccount.receive(
                chargeRequestId(command.getRechargeRequestId()),
                command.getFromBankName(),
                command.getFromBankAccountNumber(),
                command.getMoneyAmount()));
        apply(new RequestFirmBankingFinishedEvent(
                command.getRequestFirmBankingId(),
                command.getRechargeRequestId(),
                command.getMembershipId(),
                companyAccount.bankName(),
                companyAccount.bankAccountNumber(),
                command.getMoneyAmount(),
                finishedStatus(charge.status()),
                id));
        flowEvents.publish(command.getRechargeRequestId(), FlowStep.BANK_CHARGED, outcome(charge.status()), chargeReason(charge.status()),
                command.getMembershipId(), command.getMoneyAmount());
    }

    @CommandHandler
    public FirmBankingRequestAggregate(RollbackFirmBankingRequestCommand command, TransferMoneyUseCase transferMoneyUseCase,
                                       FlowEventPublisher flowEvents) {
        id = UUID.randomUUID().toString();
        String chargeRequestId = chargeRequestId(command.getRechargeRequestId());
        TransferStatus refundStatus = transferMoneyUseCase.reverse(chargeRequestId, chargeRequestId + "-refund");
        if (refundStatus == TransferStatus.FAILED) {
            log.error("The bank refused the refund of recharge {}, so the charge is not returned", command.getRechargeRequestId());
        } else {
            log.info("rolled back the recharge {}: {}", command.getRechargeRequestId(), refundStatus);
        }
        apply(rollbackAnswer(command, refundStatus));
        flowEvents.publish(command.getRechargeRequestId(), FlowStep.CHARGE_ROLLED_BACK, rollbackOutcome(refundStatus), rollbackReason(refundStatus),
                command.getMembershipId(), command.getMoneyAmount());
    }

    @EventSourcingHandler
    public void on(RequestFirmBankingFinishedEvent event) {
        id = event.getRequestFirmbankingAggregateIdentifier();
    }

    @EventSourcingHandler
    public void on(RollbackFirmBankingFinishedEvent event) {
        id = event.getRollbackFirmbankingAggregateIdentifier();
    }

    private static String chargeRequestId(String rechargeRequestId) {
        return "recharge-" + rechargeRequestId;
    }

    private static FlowOutcome outcome(TransferStatus status) {
        return switch (status) {
            case SUCCEEDED -> FlowOutcome.SUCCEEDED;
            case PENDING -> FlowOutcome.IN_PROGRESS;
            case FAILED, CANCELLED -> FlowOutcome.FAILED;
        };
    }

    private static String chargeReason(TransferStatus status) {
        return switch (status) {
            case SUCCEEDED -> null;
            case PENDING -> "The bank has not confirmed the charge yet";
            case FAILED, CANCELLED -> "The bank refused the charge";
        };
    }

    private static FlowOutcome rollbackOutcome(TransferStatus refund) {
        return switch (refund) {
            case SUCCEEDED, CANCELLED -> FlowOutcome.SUCCEEDED;
            case PENDING -> FlowOutcome.IN_PROGRESS;
            case FAILED -> FlowOutcome.FAILED;
        };
    }

    private static String rollbackReason(TransferStatus refund) {
        return switch (refund) {
            case SUCCEEDED, CANCELLED -> null;
            case PENDING -> "The bank has not confirmed the refund yet";
            case FAILED -> "The bank refused the refund";
        };
    }

    private Object rollbackAnswer(RollbackFirmBankingRequestCommand command, TransferStatus refund) {
        return switch (refund) {
            case SUCCEEDED, CANCELLED -> new RollbackFirmBankingFinishedEvent(command.getRollbackFirmbankingId(), command.getMembershipId(), id);
            case PENDING -> new RollbackFirmBankingUnconfirmedEvent(command.getRollbackFirmbankingId(), command.getMembershipId());
            case FAILED -> new RollbackFirmBankingRefusedEvent(command.getRollbackFirmbankingId(), command.getMembershipId());
        };
    }

    private static int finishedStatus(TransferStatus status) {
        return switch (status) {
            case SUCCEEDED -> RequestFirmBankingFinishedEvent.SUCCEEDED;
            case PENDING -> RequestFirmBankingFinishedEvent.UNCONFIRMED;
            case FAILED, CANCELLED -> RequestFirmBankingFinishedEvent.REFUSED;
        };
    }
}
