package com.jaypay.banking.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.banking.adapter.axon.command.CreateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.axon.command.UpdateFirmBankingRequestCommand;
import com.jaypay.banking.adapter.out.external.bank.ExternalFirmBankingRequest;
import com.jaypay.banking.adapter.out.external.bank.FirmBankingResult;
import com.jaypay.banking.adapter.out.persistence.FirmBankingRequestJpaEntity;
import com.jaypay.banking.adapter.out.persistence.FirmBankingRequestMapper;
import com.jaypay.banking.application.port.in.RequestFirmBankingCommand;
import com.jaypay.banking.application.port.in.RequestFirmBankingUseCase;
import com.jaypay.banking.application.port.in.UpdateFirmBankingCommand;
import com.jaypay.banking.application.port.in.UpdateFirmBankingUseCase;
import com.jaypay.banking.application.port.out.RequestExternalFirmBankingPort;
import com.jaypay.banking.application.port.out.RequestFirmBankingPort;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;

import jakarta.transaction.Transactional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RequestFirmBankingService implements RequestFirmBankingUseCase, UpdateFirmBankingUseCase {

    private final FirmBankingRequestMapper mapper;
    private final RequestFirmBankingPort requestFirmBankingPort;
    private final RequestExternalFirmBankingPort requestExternalFirmBankingPort;
    private final CommandGateway commandGateway;

    @Override
    public FirmBankingRequest requestFirmBanking(RequestFirmBankingCommand command) {
        FirmBankingRequestJpaEntity requestedEntity = requestFirmBankingPort.createFirmBankingRequest(
                new FirmBankingRequest.FromBankName(command.getFromBankName()),
                new FirmBankingRequest.FromBankAccountNumber(command.getFromBankAccountNumber()),
                new FirmBankingRequest.ToBankName(command.getToBankName()),
                new FirmBankingRequest.ToBankAccountNumber(command.getToBankAccountNumber()),
                new FirmBankingRequest.MoneyAmount(command.getMoneyAmount()),
                new FirmBankingRequest.FirmBankingStatus(0),
                new FirmBankingRequest.FirmBankingAggregateIdentifier("")
        );

        FirmBankingResult result = requestExternalFirmBankingPort.requestExternalFirmBanking(new ExternalFirmBankingRequest(
                command.getFromBankName(),
                command.getFromBankAccountNumber(),
                command.getToBankName(),
                command.getToBankAccountNumber(),
                command.getMoneyAmount()
        ));

        UUID randomUUID = UUID.randomUUID();
        requestedEntity.setUuid(randomUUID.toString());

        if (result.getResultCode() == 0) {
            requestedEntity.setFirmBankingStatus(1);
        } else {
            requestedEntity.setFirmBankingStatus(2);
        }

        return mapper.mapToDomainEntity(requestFirmBankingPort.modifyFirmBankingRequest(requestedEntity), randomUUID);
    }

    @Override
    public void requestFirmBankingByEvent(RequestFirmBankingCommand command) {
        CreateFirmBankingRequestCommand eventCommand = CreateFirmBankingRequestCommand.builder()
                .toBankName(command.getToBankName())
                .toBankAccountNumber(command.getToBankAccountNumber())
                .fromBankName(command.getFromBankName())
                .fromBankAccountNumber(command.getFromBankAccountNumber())
                .moneyAmount(command.getMoneyAmount())
                .build();

        commandGateway.send(eventCommand).whenComplete(
                (result, throwable) -> {
                    if (throwable != null) {
                        log.warn("{}", throwable.getMessage());
                    } else {
                        log.info("CreateFirmBankingRequestCommand completed, Aggregate ID: {}", result);

                        FirmBankingRequestJpaEntity requestedEntity = requestFirmBankingPort.createFirmBankingRequest(
                                new FirmBankingRequest.FromBankName(command.getFromBankName()),
                                new FirmBankingRequest.FromBankAccountNumber(command.getFromBankAccountNumber()),
                                new FirmBankingRequest.ToBankName(command.getToBankName()),
                                new FirmBankingRequest.ToBankAccountNumber(command.getToBankAccountNumber()),
                                new FirmBankingRequest.MoneyAmount(command.getMoneyAmount()),
                                new FirmBankingRequest.FirmBankingStatus(0),
                                new FirmBankingRequest.FirmBankingAggregateIdentifier(result.toString())
                        );

                        FirmBankingResult firmBankingResult = requestExternalFirmBankingPort.requestExternalFirmBanking(new ExternalFirmBankingRequest(
                                command.getFromBankName(),
                                command.getFromBankAccountNumber(),
                                command.getToBankName(),
                                command.getToBankAccountNumber(),
                                command.getMoneyAmount()
                        ));

                        if (firmBankingResult.getResultCode() == 0) {
                            requestedEntity.setFirmBankingStatus(1);
                        } else {
                            requestedEntity.setFirmBankingStatus(2);
                        }

                        requestFirmBankingPort.modifyFirmBankingRequest(requestedEntity);
                    }
                }
        );

    }

    @Override
    public void updateFirmBankingByEvent(UpdateFirmBankingCommand command) {

        UpdateFirmBankingRequestCommand updateFirmBankingRequestCommand = new UpdateFirmBankingRequestCommand(
                command.getFirmBankingAggregateIdentifier(), command.getFirmBankingStatus()
        );

        commandGateway.send(updateFirmBankingRequestCommand).whenComplete(
                (result, throwable) -> {
                        if (throwable != null) {
                            log.warn("{}", throwable.getMessage());
                        } else {
                            log.info("UpdateFirmBankingRequestCommand completed, Aggregate ID: {}", result);
                            FirmBankingRequestJpaEntity entity = requestFirmBankingPort.getFirmBankingRequest(
                                    new FirmBankingRequest.FirmBankingAggregateIdentifier(command.getFirmBankingAggregateIdentifier()));

                            entity.setFirmBankingStatus(command.getFirmBankingStatus());
                            requestFirmBankingPort.modifyFirmBankingRequest(entity);
                        }
                    }
                );

    }
}
