package com.jaypay.banking.adapter.axon.aggregate;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.banking.adapter.axon.command.CreateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.command.UpdateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.event.FirmBankingRequestCreatedEvent;
import com.jaypay.banking.adapter.axon.event.FirmBankingRequestUpdatedEvent;
import com.jaypay.banking.adapter.out.external.bank.ExternalFirmBankingRequest;
import com.jaypay.banking.adapter.out.external.bank.FirmBankingResult;
import com.jaypay.banking.application.port.out.RequestExternalFirmBankingPort;
import com.jaypay.banking.application.port.out.RequestFirmBankingPort;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.common.event.RequestFirmBankingCommand;
import com.jaypay.common.event.RollbackFirmBankingFinishedEvent;
import com.jaypay.common.event.RollbackFirmBankingRequestCommand;
import lombok.Data;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate()
@Data
@Slf4j
public class FirmBankingRequestAggregate {

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
    public FirmBankingRequestAggregate(RequestFirmBankingCommand command, RequestFirmBankingPort firmbankingPort, RequestExternalFirmBankingPort externalFirmbankingPort) {
        log.info("FirmbankingRequestAggregate Handler");
        id = command.getAggregateIdentifier();

        firmbankingPort.createFirmBankingRequest(
                new FirmBankingRequest.FromBankName(command.getToBankName()),
                new FirmBankingRequest.FromBankAccountNumber(command.getToBankAccountNumber()),
                new FirmBankingRequest.ToBankName("jaypay-company"),
                new FirmBankingRequest.ToBankAccountNumber("123-333-9999"),
                new FirmBankingRequest.MoneyAmount(command.getMoneyAmount()),
                new FirmBankingRequest.FirmBankingStatus(0),
                new FirmBankingRequest.FirmBankingAggregateIdentifier(id));

        FirmBankingResult firmbankingResult = externalFirmbankingPort.requestExternalFirmBanking(
                new ExternalFirmBankingRequest(
                        command.getFromBankName(),
                        command.getFromBankAccountNumber(),
                        command.getToBankName(),
                        command.getToBankAccountNumber(),
                        command.getMoneyAmount()
                ));

        int resultCode = firmbankingResult.getResultCode();

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
    public FirmBankingRequestAggregate(@NotNull RollbackFirmBankingRequestCommand command, RequestFirmBankingPort firmbankingPort, RequestExternalFirmBankingPort externalFirmbankingPort) {
        log.info("RollbackFirmbankingRequestCommand Handler");
        id = UUID.randomUUID().toString();

        firmbankingPort.createFirmBankingRequest(
                new FirmBankingRequest.FromBankName("jaypay-company"),
                new FirmBankingRequest.FromBankAccountNumber("123-333-9999"),
                new FirmBankingRequest.ToBankName(command.getBankName()),
                new FirmBankingRequest.ToBankAccountNumber(command.getBankAccountNumber()),
                new FirmBankingRequest.MoneyAmount(command.getMoneyAmount()),
                new FirmBankingRequest.FirmBankingStatus(0),
                new FirmBankingRequest.FirmBankingAggregateIdentifier(id));

        FirmBankingResult result = externalFirmbankingPort.requestExternalFirmBanking(
                new ExternalFirmBankingRequest(
                        "jaypay-company",
                        "123-333-9999",
                        command.getBankName(),
                        command.getBankAccountNumber(),
                        command.getMoneyAmount()
                ));

        int res = result.getResultCode();

        if (res == 0) {
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
