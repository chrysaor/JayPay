package com.jaypay.banking.application.service;

import com.jaypay.banking.application.port.in.ExecuteFirmBankingCommand;
import com.jaypay.banking.application.port.in.ExecuteFirmBankingUseCase;
import com.jaypay.banking.application.port.in.RequestFirmBankingCommand;
import com.jaypay.banking.application.port.in.RequestFirmBankingUseCase;
import com.jaypay.banking.application.port.in.UpdateFirmBankingCommand;
import com.jaypay.banking.application.port.in.UpdateFirmBankingUseCase;
import com.jaypay.banking.application.port.out.FirmBankingAggregatePort;
import com.jaypay.banking.application.port.out.RequestExternalFirmBankingPort;
import com.jaypay.banking.application.port.out.RequestFirmBankingPort;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.banking.domain.FirmBankingRequest.FirmBankingAggregateIdentifier;
import com.jaypay.banking.domain.FirmBankingRequest.FirmBankingStatus;
import com.jaypay.banking.domain.FirmBankingTransfer;
import com.jaypay.common.UseCase;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RequestFirmBankingService implements RequestFirmBankingUseCase, UpdateFirmBankingUseCase, ExecuteFirmBankingUseCase {

    private final RequestFirmBankingPort requestFirmBankingPort;
    private final RequestExternalFirmBankingPort requestExternalFirmBankingPort;
    private final FirmBankingAggregatePort firmBankingAggregatePort;

    @Override
    public FirmBankingRequest requestFirmBanking(RequestFirmBankingCommand command) {
        FirmBankingTransfer transfer = transferOf(command);
        return requestFirmBankingPort.createFirmBankingRequest(
                transfer, requestExternalFirmBanking(transfer), new FirmBankingAggregateIdentifier(""));
    }

    @Override
    public void requestFirmBankingByEvent(RequestFirmBankingCommand command) {
        FirmBankingTransfer transfer = transferOf(command);
        firmBankingAggregatePort.createFirmBankingAggregate(transfer).whenComplete((aggregateIdentifier, throwable) -> {
            if (throwable != null) {
                log.warn("{}", throwable.getMessage());
            } else {
                log.info("CreateFirmBankingRequestCommand completed, Aggregate ID: {}", aggregateIdentifier);
                requestFirmBankingPort.createFirmBankingRequest(
                        transfer, requestExternalFirmBanking(transfer), new FirmBankingAggregateIdentifier(aggregateIdentifier));
            }
        });
    }

    @Override
    public void updateFirmBankingByEvent(UpdateFirmBankingCommand command) {
        firmBankingAggregatePort.updateFirmBankingAggregate(command.firmBankingAggregateIdentifier(), command.firmBankingStatus())
                .whenComplete((aggregateIdentifier, throwable) -> {
                    if (throwable != null) {
                        log.warn("{}", throwable.getMessage());
                    } else {
                        log.info("UpdateFirmBankingRequestCommand completed, Aggregate ID: {}", aggregateIdentifier);
                        requestFirmBankingPort.updateFirmBankingStatus(
                                new FirmBankingAggregateIdentifier(command.firmBankingAggregateIdentifier()),
                                new FirmBankingStatus(command.firmBankingStatus()));
                    }
                });
    }

    @Override
    public int executeFirmBanking(ExecuteFirmBankingCommand command) {
        requestFirmBankingPort.createFirmBankingRequest(
                command.recordedTransfer(), new FirmBankingStatus(0), new FirmBankingAggregateIdentifier(command.aggregateIdentifier()));
        return requestExternalFirmBankingPort.requestExternalFirmBanking(command.requestedTransfer());
    }

    private FirmBankingStatus requestExternalFirmBanking(FirmBankingTransfer transfer) {
        return FirmBankingStatus.fromResultCode(requestExternalFirmBankingPort.requestExternalFirmBanking(transfer));
    }

    private static FirmBankingTransfer transferOf(RequestFirmBankingCommand command) {
        return new FirmBankingTransfer(
                command.fromBankName(),
                command.fromBankAccountNumber(),
                command.toBankName(),
                command.toBankAccountNumber(),
                command.moneyAmount());
    }
}
