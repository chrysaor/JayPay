package com.jaypay.banking.application.service;

import com.jaypay.banking.adapter.out.external.bank.ExternalFirmBankingRequest;
import com.jaypay.banking.adapter.out.external.bank.FirmBankingResult;
import com.jaypay.banking.adapter.out.persistence.FirmBankingRequestJpaEntity;
import com.jaypay.banking.adapter.out.persistence.FirmBankingRequestMapper;
import com.jaypay.banking.application.port.in.RequestFirmBankingCommand;
import com.jaypay.banking.application.port.in.RequestFirmBankingUseCase;
import com.jaypay.banking.application.port.out.RequestExternalFirmBankingPort;
import com.jaypay.banking.application.port.out.RequestFirmBankingPort;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
public class RequestFirmBankingService implements RequestFirmBankingUseCase {

    private final FirmBankingRequestMapper mapper;
    private final RequestFirmBankingPort requestFirmBankingPort;
    private final RequestExternalFirmBankingPort requestExternalFirmBankingPort;

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
                new FirmBankingRequest.FirmBankingStatus(0)
        );

        // 2. 외부 은행에 펌뱅킹 요청
        FirmBankingResult result = requestExternalFirmBankingPort.requestExternalFirmBanking(new ExternalFirmBankingRequest(
                command.getFromBankName(),
                command.getFromBankAccountNumber(),
                command.getToBankName(),
                command.getToBankAccountNumber()
        ));

        // Transactional UUID
        UUID randomUUID = UUID.randomUUID();
        requestedEntity.setUuid(randomUUID);

        // 3. 결과에 따라서 1번에서 작성했던 FirmBankingRequest 정보를 업데이트
        if (result.getResultCode() == 0) {
            // Success
            requestedEntity.setFirmBankingStatus(1);
        } else {
            // Failure
            requestedEntity.setFirmBankingStatus(2);
        }

        // 4. 바뀐 상태값 기준 save -> 리턴
        requestedEntity = requestFirmBankingPort.modifyFirmBankingRequest(requestedEntity);

        return mapper.mapToDomainEntity(requestedEntity);
    }

}
