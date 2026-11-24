package com.jaypay.money.adapter.axon.saga;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.event.*;
import com.jaypay.money.adapter.axon.event.RechargingRequestCreatedEvent;
import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;
import com.jaypay.money.application.port.out.IncreaseMoneyPort;
import com.jaypay.money.domain.MemberMoney;
import lombok.NoArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;


@Saga
@NoArgsConstructor
@Slf4j
public class MoneyRechargeSaga {

    private transient CommandGateway commandGateway;

    @Autowired
    public void setCommandGateway(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @StartSaga
    @SagaEventHandler(associationProperty = "rechargingRequestId")
    public void handle(RechargingRequestCreatedEvent event) {
        log.info("RechargingRequestCreatedEvent Start saga");

        String checkRegisteredBankAccountId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("checkRegisteredBankAccountId", checkRegisteredBankAccountId);

        commandGateway.send(new CheckRegisteredBankAccountCommand(
                event.getRegisteredBankAccountAggregateIdentifier(),
                event.getRechargingRequestId(),
                event.getMembershipId(),
                checkRegisteredBankAccountId,
                event.getBankName(),
                event.getBankAccountNumber(),
                event.getAmount())
        ).whenComplete(
                (result, throwable) -> {
                    if (throwable != null) {
                        log.warn("CheckRegisteredBankAccountCommand Command failed");
                        log.warn("{}", throwable.getMessage());
                    } else {
                        log.info("CheckRegisteredBankAccountCommand Command success");
                    }
                }
        );

    }

    @SagaEventHandler(associationProperty = "checkRegisteredBankAccountId")
    public void handle(CheckedRegisteredBankAccountEvent event) {
        log.info("CheckedRegisteredBankAccountEvent saga: {}", event);
        boolean status = event.isChecked();
        if (status) {
            log.info("CheckedRegisteredBankAccountEvent event success");
        } else {
            log.warn("CheckedRegisteredBankAccountEvent event Failed");
        }

        String requestFirmBankingId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("requestFirmBankingId", requestFirmBankingId);

        commandGateway.send(new RequestFirmBankingCommand(
                requestFirmBankingId,
                event.getFirmbankingRequestAggregateIdentifier(),
                event.getRechargingRequestId(),
                event.getMembershipId(),
                event.getFromBankName(),
                event.getFromBankAccountNumber(),
                "jaypay-company",
                "123456789",
                event.getAmount()
        )).whenComplete(
                (result, throwable) -> {
                    if (throwable != null) {
                        log.warn("{}", throwable.getMessage());
                        log.warn("RequestFirmBankingCommand Command failed");
                    } else {
                        log.info("RequestFirmBankingCommand Command success");
                    }
                }
        );
    }

    @SagaEventHandler(associationProperty = "requestFirmBankingId")
    public void handle(RequestFirmBankingFinishedEvent event, IncreaseMoneyPort increaseMoneyPort) {
        log.info("RequestFirmBankingFinishedEvent saga: {}", event);

        boolean status = event.getStatus() == 0;
        if (status) {
            log.info("RequestFirmBankingFinishedEvent event success");
        } else {
            log.warn("RequestFirmBankingFinishedEvent event Failed");
        }

        MemberMoneyJpaEntity resultEntity =
                increaseMoneyPort.increaseMoney(
                        new MemberMoney.MembershipId(event.getMembershipId()),
                        event.getMoneyAmount()
                );

        if (resultEntity == null) {
            String rollbackFirmBankingId = UUID.randomUUID().toString();
            SagaLifecycle.associateWith("rollbackFirmBankingId", rollbackFirmBankingId);
            commandGateway.send(new RollbackFirmBankingRequestCommand(
                    rollbackFirmBankingId,
                    event.getRequestFirmbankingAggregateIdentifier(),
                    event.getRechargingRequestId(),
                    event.getMembershipId(),
                    event.getToBankName(),
                    event.getToBankAccountNumber(),
                    event.getMoneyAmount()
            )).whenComplete(
                    (result, throwable) -> {
                        if (throwable != null) {
                            log.warn("RollbackFirmbankingRequestCommand Command failed");
                            log.warn("{}", throwable.getMessage());
                        } else {
                            log.info("Saga success : {}", result);
                            SagaLifecycle.end();
                        }
                    }
            );
        } else {
            SagaLifecycle.end();
        }
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "rollbackFirmBankingId")
    public void handle(RollbackFirmBankingFinishedEvent event) {
        log.info("RollbackFirmBankingFinishedEvent saga {}", event);
    }

}
