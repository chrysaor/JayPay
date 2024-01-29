package com.jaypay.money.adapter.axon.saga;

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
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;


@Saga
@NoArgsConstructor
public class MoneyRechargeSaga {

    private transient CommandGateway commandGateway;

    @Autowired
    public void setCommandGateway(@NotNull CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @StartSaga
    @SagaEventHandler(associationProperty = "rechargingRequestId")
    public void handle(RechargingRequestCreatedEvent event) {
        System.out.println("RechargingRequestCreatedEvent Start saga");

        // Unique id setting in the SAGA process
        String checkRegisteredBankAccountId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("checkRegisteredBankAccountId", checkRegisteredBankAccountId);

        // "Charge request" is started.

        // To check an account of banking service (RegisteredBankAccount)
        // CheckRegisteredBankAccountCommand -> Check Bank Account
        // -> Axon server -> Banking Service -> Common

        // 1. In the axon framework, the modification of all aggregates can do with an aggregate unit
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
                        System.out.println("CheckRegisteredBankAccountCommand Command failed");
                        System.out.println(throwable.getMessage());
                    } else {
                        System.out.println("CheckRegisteredBankAccountCommand Command success");
                    }
                }
        );

    }

    @SagaEventHandler(associationProperty = "checkRegisteredBankAccountId")
    public void handle(CheckedRegisteredBankAccountEvent event) {
        System.out.println("CheckedRegisteredBankAccountEvent saga: " + event.toString());
        boolean status = event.isChecked();
        if (status) {
            System.out.println("CheckedRegisteredBankAccountEvent event success");
        } else {
            System.out.println("CheckedRegisteredBankAccountEvent event Failed");
        }

        String requestFirmBankingId = UUID.randomUUID().toString();
        SagaLifecycle.associateWith("requestFirmBankingId", requestFirmBankingId);

        // 2. Remittance request
        // Customer account -> Company account
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
                        System.out.println(throwable.getMessage());
                        System.out.println("RequestFirmBankingCommand Command failed");
                    } else {
                        System.out.println("RequestFirmBankingCommand Command success");
                    }
                }
        );
    }

    @SagaEventHandler(associationProperty = "requestFirmBankingId")
    public void handle(RequestFirmBankingFinishedEvent event, IncreaseMoneyPort increaseMoneyPort) {
        System.out.println("RequestFirmBankingFinishedEvent saga: " + event.toString());

        boolean status = event.getStatus() == 0;
        if (status) {
            System.out.println("RequestFirmBankingFinishedEvent event success");
        } else {
            System.out.println("RequestFirmBankingFinishedEvent event Failed");
        }

        // DB Update
        MemberMoneyJpaEntity resultEntity =
                increaseMoneyPort.increaseMoney(
                        new MemberMoney.MembershipId(event.getMembershipId()),
                        event.getMoneyAmount()
                );

        if (resultEntity == null) {
            // If it failed, have to rollback
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
                            System.out.println("RollbackFirmbankingRequestCommand Command failed");
                            System.out.println(throwable.getMessage());
                        } else {
                            System.out.println("Saga success : "+ result.toString());
                            SagaLifecycle.end();
                        }
                    }
            );
        } else {
            // 성공 시, saga 종료.
            SagaLifecycle.end();
        }
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "rollbackFirmBankingId")
    public void handle(RollbackFirmBankingFinishedEvent event) {
        System.out.println("RollbackFirmBankingFinishedEvent saga" + event.toString());
    }

}
