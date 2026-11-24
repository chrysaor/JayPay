package com.jaypay.banking.adapter.in.axon;

import com.jaypay.banking.adapter.axon.command.CreateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.command.UpdateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.event.FirmBankingRequestCreatedEvent;
import com.jaypay.banking.adapter.axon.event.FirmBankingRequestUpdatedEvent;
import com.jaypay.banking.application.port.in.ExecuteFirmBankingCommand;
import com.jaypay.banking.application.port.in.ExecuteFirmBankingUseCase;
import com.jaypay.banking.domain.FirmBankingTransfer;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate()
@Data
@Slf4j
public class FirmBankingRequestAggregate {

    private static final String COMPANY_BANK_NAME = "jaypay-company";
    private static final String COMPANY_BANK_ACCOUNT_NUMBER = "123-333-9999";

    @AggregateIdentifier
    private String id;
    private String fromBankName;
    private String fromBankAccountNumber;
    private String toBankName;
    private String toBankAccountNumber;
    private int moneyAmount;
    private int firmbankingStatus;

    public FirmBankingRequestAggregate() {

    }

    @CommandHandler
    public FirmBankingRequestAggregate(CreateFirmBankingRequestCommand command) {
        log.info("CreateFirmBankingRequestCommand Handler");

        apply(new FirmBankingRequestCreatedEvent(command.getFromBankName(), command.getFromBankAccountNumber(), command.getToBankName(), command.getToBankAccountNumber(), command.getMoneyAmount()));
    }

    @CommandHandler
    public FirmBankingRequestAggregate(RequestFirmBankingCommand command, ExecuteFirmBankingUseCase executeFirmBankingUseCase) {
        log.info("FirmbankingRequestAggregate Handler");
        id = command.getAggregateIdentifier();

        int resultCode = executeFirmBankingUseCase.executeFirmBanking(new ExecuteFirmBankingCommand(
                id,
                new FirmBankingTransfer(
                        command.getToBankName(),
                        command.getToBankAccountNumber(),
                        COMPANY_BANK_NAME,
                        COMPANY_BANK_ACCOUNT_NUMBER,
                        command.getMoneyAmount()),
                new FirmBankingTransfer(
                        command.getFromBankName(),
                        command.getFromBankAccountNumber(),
                        command.getToBankName(),
                        command.getToBankAccountNumber(),
                        command.getMoneyAmount())
        ));

        apply(new RequestFirmBankingFinishedEvent(
                command.getRequestFirmBankingId(),
                command.getRechargeRequestId(),
                command.getMembershipId(),
                command.getToBankName(),
                command.getToBankAccountNumber(),
                command.getMoneyAmount(),
                resultCode,
                id
        ));
    }

    @CommandHandler
    public FirmBankingRequestAggregate(RollbackFirmBankingRequestCommand command, ExecuteFirmBankingUseCase executeFirmBankingUseCase) {
        log.info("RollbackFirmbankingRequestCommand Handler");
        id = UUID.randomUUID().toString();

        FirmBankingTransfer refund = new FirmBankingTransfer(
                COMPANY_BANK_NAME,
                COMPANY_BANK_ACCOUNT_NUMBER,
                command.getBankName(),
                command.getBankAccountNumber(),
                command.getMoneyAmount());

        int resultCode = executeFirmBankingUseCase.executeFirmBanking(new ExecuteFirmBankingCommand(id, refund, refund));

        if (resultCode == 0) {
            log.info("ExternalFirmBankingRequest success");
        } else {
            log.warn("ExternalFirmBankingRequest failed");
        }

        apply(new RollbackFirmBankingFinishedEvent(
                command.getRollbackFirmbankingId(),
                command.getMembershipId(),
                id)
        );
    }

    @CommandHandler
    public String handle(UpdateFirmBankingRequestCommand command) {
        log.info("UpdateFirmBankingRequestCommand Handler");

        id = command.getAggregateIdentifier();
        apply(new FirmBankingRequestUpdatedEvent(command.getFirmbankingStatus()));

        return id;
    }

    @EventSourcingHandler
    public void on(FirmBankingRequestCreatedEvent event) {
        log.info("FirmBankingRequestCreatedEvent Sourcing Handler");

        id = UUID.randomUUID().toString();
        fromBankName = event.getFromBankName();
        fromBankAccountNumber = event.getToBankAccountNumber();
        toBankName = event.getToBankName();
        toBankAccountNumber = event.getToBankAccountNumber();
    }

    @EventSourcingHandler
    public void on(FirmBankingRequestUpdatedEvent event) {
        log.info("FirmBankingRequestUpdatedEvent Sourcing Handler");

        firmbankingStatus = event.getFirmBankingStatus();
    }

}
