package com.jaypay.money.adapter.in.axon;

import com.jaypay.common.event.CheckMembershipCommand;
import com.jaypay.common.event.CheckRegisteredBankAccountCommand;
import com.jaypay.common.event.CheckedMembershipEvent;
import com.jaypay.common.event.CheckedRegisteredBankAccountEvent;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import com.jaypay.common.event.WalletRechargedEvent;
import com.jaypay.money.adapter.axon.event.RechargingRequestCreatedEvent;
import com.jaypay.money.application.port.in.ChangeMoneyUseCase;
import com.jaypay.money.domain.RechargeFailure;
import com.jaypay.money.domain.RequestId;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.UUID;

@Saga
@NoArgsConstructor
@Slf4j
public class MoneyRechargeSaga {

    private static final String DEADLINE = "recharge-deadline";
    private static final String COMPANY_BANK_NAME = "jaypay-company";
    private static final String COMPANY_BANK_ACCOUNT_NUMBER = "123456789";

    private transient CommandGateway commandGateway;
    private transient EventGateway eventGateway;
    private transient DeadlineManager deadlineManager;
    private transient Duration deadline;
    private transient ChangeMoneyUseCase changeMoneyUseCase;

    private String rechargingRequestId;
    private String membershipId;
    private String bankName;
    private String bankAccountNumber;
    private long amount;
    private String registeredBankAccountAggregateIdentifier;
    private String deadlineId;

    @Autowired
    public void setCommandGateway(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Autowired
    public void setEventGateway(EventGateway eventGateway) {
        this.eventGateway = eventGateway;
    }

    @Autowired
    public void setDeadlineManager(DeadlineManager deadlineManager) {
        this.deadlineManager = deadlineManager;
    }

    @Autowired
    public void setDeadline(@Value("${money.recharge.deadline}") Duration deadline) {
        this.deadline = deadline;
    }

    @Autowired
    public void setChangeMoneyUseCase(ChangeMoneyUseCase changeMoneyUseCase) {
        this.changeMoneyUseCase = changeMoneyUseCase;
    }

    @StartSaga
    @SagaEventHandler(associationProperty = "rechargingRequestId")
    public void handle(RechargingRequestCreatedEvent event) {
        rechargingRequestId = event.rechargingRequestId();
        membershipId = event.membershipId();
        bankName = event.bankName();
        bankAccountNumber = event.bankAccountNumber();
        amount = event.amount();
        registeredBankAccountAggregateIdentifier = event.registeredBankAccountAggregateIdentifier();
        deadlineId = deadlineManager.schedule(deadline, DEADLINE);

        String checkMembershipId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("checkMembershipId", checkMembershipId);
        commandGateway.send(new CheckMembershipCommand(checkMembershipId, rechargingRequestId, membershipId)).exceptionally(failure -> {
            log.warn("The membership check of recharge {} failed, so the deadline ends it: {}", rechargingRequestId, failure.getMessage());
            return null;
        });
    }

    @SagaEventHandler(associationProperty = "checkMembershipId")
    public void handle(CheckedMembershipEvent event) {
        if (!event.valid()) {
            log.warn("Member {} of recharge {} is not a valid member, so the recharge fails", membershipId, rechargingRequestId);
            changeMoneyUseCase.failRecharging(new RequestId(rechargingRequestId), RechargeFailure.MEMBER_NOT_CONFIRMED);
            end();
            return;
        }
        String checkRegisteredBankAccountId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("checkRegisteredBankAccountId", checkRegisteredBankAccountId);
        commandGateway.send(new CheckRegisteredBankAccountCommand(
                registeredBankAccountAggregateIdentifier,
                rechargingRequestId,
                membershipId,
                checkRegisteredBankAccountId,
                bankName,
                bankAccountNumber,
                amount)
        ).exceptionally(failure -> {
            log.warn("The account check of recharge {} failed, so the deadline ends it: {}", rechargingRequestId, failure.getMessage());
            return null;
        });
    }

    @SagaEventHandler(associationProperty = "checkRegisteredBankAccountId")
    public void handle(CheckedRegisteredBankAccountEvent event) {
        if (!event.isChecked()) {
            log.warn("The bank account of recharge {} did not pass the check, so the recharge fails", rechargingRequestId);
            changeMoneyUseCase.failRecharging(new RequestId(rechargingRequestId), RechargeFailure.BANK_ACCOUNT_NOT_CONFIRMED);
            end();
            return;
        }
        String requestFirmBankingId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("requestFirmBankingId", requestFirmBankingId);
        commandGateway.send(new RequestFirmBankingCommand(
                requestFirmBankingId,
                event.getFirmbankingRequestAggregateIdentifier(),
                rechargingRequestId,
                membershipId,
                bankName,
                bankAccountNumber,
                COMPANY_BANK_NAME,
                COMPANY_BANK_ACCOUNT_NUMBER,
                amount)
        ).exceptionally(failure -> {
            log.warn("The charge of recharge {} failed, so the deadline ends it: {}", rechargingRequestId, failure.getMessage());
            return null;
        });
    }

    @SagaEventHandler(associationProperty = "requestFirmBankingId")
    public void handle(RequestFirmBankingFinishedEvent event) {
        switch (event.getStatus()) {
            case RequestFirmBankingFinishedEvent.SUCCEEDED -> {
                if (changeMoneyUseCase.finishRecharging(new RequestId(rechargingRequestId))) {
                    eventGateway.publish(new WalletRechargedEvent(rechargingRequestId, membershipId, amount));
                    end();
                } else {
                    rollBack();
                }
            }
            case RequestFirmBankingFinishedEvent.REFUSED -> {
                log.warn("The bank refused the charge of recharge {}, so the recharge fails", rechargingRequestId);
                changeMoneyUseCase.failRecharging(new RequestId(rechargingRequestId), RechargeFailure.CHARGE_REFUSED);
                end();
            }
            default -> log.warn("The bank did not confirm the charge of recharge {}, so the deadline settles it", rechargingRequestId);
        }
    }

    @DeadlineHandler(deadlineName = DEADLINE)
    public void onDeadline() {
        deadlineId = null;
        if (changeMoneyUseCase.failRecharging(new RequestId(rechargingRequestId), RechargeFailure.DEADLINE_PASSED)) {
            log.warn("Recharge {} did not finish before its deadline, so it fails and its charge is rolled back", rechargingRequestId);
            rollBack();
        } else {
            SagaLifecycle.end();
        }
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "rollbackFirmBankingId")
    public void handle(RollbackFirmBankingFinishedEvent event) {
        log.info("The charge of recharge {} is rolled back", rechargingRequestId);
    }

    private void rollBack() {
        cancelDeadline();
        String rollbackFirmBankingId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("rollbackFirmBankingId", rollbackFirmBankingId);
        commandGateway.send(new RollbackFirmBankingRequestCommand(
                rollbackFirmBankingId,
                UUID.randomUUID().toString(),
                rechargingRequestId,
                membershipId,
                bankName,
                bankAccountNumber,
                amount)
        ).exceptionally(failure -> {
            log.error("The rollback of recharge {} failed: {}", rechargingRequestId, failure.getMessage());
            return null;
        });
    }

    private void end() {
        cancelDeadline();
        SagaLifecycle.end();
    }

    private void cancelDeadline() {
        if (deadlineId != null) {
            deadlineManager.cancelSchedule(DEADLINE, deadlineId);
            deadlineId = null;
        }
    }
}
