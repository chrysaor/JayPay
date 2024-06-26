package com.jaypay.banking.application.service;

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

import javax.transaction.Transactional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
public class RequestFirmBankingService implements RequestFirmBankingUseCase, UpdateFirmBankingUseCase {

    private final FirmBankingRequestMapper mapper;
    private final RequestFirmBankingPort requestFirmBankingPort;
    private final RequestExternalFirmBankingPort requestExternalFirmBankingPort;
    private final CommandGateway commandGateway;

    @Override
    public FirmBankingRequest requestFirmBanking(RequestFirmBankingCommand command) {
        // Business logic
        // a -> b 계좌

        // 1. 요청에 대한 정보 획득
        FirmBankingRequestJpaEntity requestedEntity = requestFirmBankingPort.createFirmBankingRequest(
                new FirmBankingRequest.FromBankName(command.getFromBankName()),
                new FirmBankingRequest.FromBankAccountNumber(command.getFromBankAccountNumber()),
                new FirmBankingRequest.ToBankName(command.getToBankName()),
                new FirmBankingRequest.ToBankAccountNumber(command.getToBankAccountNumber()),
                new FirmBankingRequest.MoneyAmount(command.getMoneyAmount()),
                new FirmBankingRequest.FirmBankingStatus(0),
                new FirmBankingRequest.FirmBankingAggregateIdentifier("")
        );

        // 2. 외부 은행에 펌뱅킹 요청
        FirmBankingResult result = requestExternalFirmBankingPort.requestExternalFirmBanking(new ExternalFirmBankingRequest(
                command.getFromBankName(),
                command.getFromBankAccountNumber(),
                command.getToBankName(),
                command.getToBankAccountNumber(),
                command.getMoneyAmount()
        ));

        // Transactional UUID
        UUID randomUUID = UUID.randomUUID();
        requestedEntity.setUuid(randomUUID.toString());

        // 3. 결과에 따라서 1번에서 작성했던 FirmBankingRequest 정보를 업데이트
        if (result.getResultCode() == 0) {
            // Success
            requestedEntity.setFirmBankingStatus(1);
        } else {
            // Failure
            requestedEntity.setFirmBankingStatus(2);
        }

        // 4. 바뀐 상태값 기준 save -> 리턴
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
                        // Failure
                        System.out.println(throwable.getMessage());
                    } else {
                        // Success
                        System.out.println("CreateFirmBankingRequestCommand completed, Aggregate ID: " + result.toString());

                        // 1. 요청에 대한 정보 획득
                        FirmBankingRequestJpaEntity requestedEntity = requestFirmBankingPort.createFirmBankingRequest(
                                new FirmBankingRequest.FromBankName(command.getFromBankName()),
                                new FirmBankingRequest.FromBankAccountNumber(command.getFromBankAccountNumber()),
                                new FirmBankingRequest.ToBankName(command.getToBankName()),
                                new FirmBankingRequest.ToBankAccountNumber(command.getToBankAccountNumber()),
                                new FirmBankingRequest.MoneyAmount(command.getMoneyAmount()),
                                new FirmBankingRequest.FirmBankingStatus(0),
                                new FirmBankingRequest.FirmBankingAggregateIdentifier(result.toString())
                        );

                        // 펌뱅킹 요청
                        FirmBankingResult firmBankingResult = requestExternalFirmBankingPort.requestExternalFirmBanking(new ExternalFirmBankingRequest(
                                command.getFromBankName(),
                                command.getFromBankAccountNumber(),
                                command.getToBankName(),
                                command.getToBankAccountNumber(),
                                command.getMoneyAmount()
                        ));

                        // 3. 결과에 따라서 1번에서 작성했던 FirmBankingRequest 정보를 업데이트
                        if (firmBankingResult.getResultCode() == 0) {
                            // Success
                            requestedEntity.setFirmBankingStatus(1);
                        } else {
                            // Failure
                            requestedEntity.setFirmBankingStatus(2);
                        }

                        requestFirmBankingPort.modifyFirmBankingRequest(requestedEntity);
                    }
                }
        );
        // Command -> Event Sourcing

    }

    @Override
    public void updateFirmBankingByEvent(UpdateFirmBankingCommand command) {

        UpdateFirmBankingRequestCommand updateFirmBankingRequestCommand = new UpdateFirmBankingRequestCommand(
                command.getFirmBankingAggregateIdentifier(), command.getFirmBankingStatus()
        );

        commandGateway.send(updateFirmBankingRequestCommand).whenComplete(
                (result, throwable) -> {
                        if (throwable != null) {
                            System.out.println(throwable.getMessage());
                        } else {
                            System.out.println("UpdateFirmBankingRequestCommand completed, Aggregate ID: " + result.toString());
                            FirmBankingRequestJpaEntity entity = requestFirmBankingPort.getFirmBankingRequest(
                                    new FirmBankingRequest.FirmBankingAggregateIdentifier(command.getFirmBankingAggregateIdentifier()));

                            // 외부 은행과의 연동
                            // 롤백의 경우 status 변경 가능
                            entity.setFirmBankingStatus(command.getFirmBankingStatus());
                            requestFirmBankingPort.modifyFirmBankingRequest(entity);
                        }
                    }
                );

    }
}
