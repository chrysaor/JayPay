package com.jaypay.banking.adapter.out.persistence;

import com.jaypay.banking.application.port.out.RequestFirmBankingPort;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.banking.domain.FirmBankingTransfer;
import com.jaypay.common.PersistenceAdapter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
public class FirmBankingRequestPersistenceAdapter implements RequestFirmBankingPort {

    private final SpringDataFirmBankingRequestRepository firmBankingRequestRepository;

    @Override
    public FirmBankingRequest createFirmBankingRequest(FirmBankingTransfer transfer, FirmBankingRequest.FirmBankingStatus firmBankingStatus, FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier) {
        return toDomain(firmBankingRequestRepository.save(new FirmBankingRequestJpaEntity(
                transfer.fromBankName(),
                transfer.fromBankAccountNumber(),
                transfer.toBankName(),
                transfer.toBankAccountNumber(),
                transfer.moneyAmount(),
                firmBankingStatus.getFirmBankingStatus(),
                UUID.randomUUID(),
                firmBankingAggregateIdentifier.getFirmBankingAggregateIdentifier()
        )));
    }

    @Override
    public void updateFirmBankingStatus(FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier, FirmBankingRequest.FirmBankingStatus firmBankingStatus) {
        FirmBankingRequestJpaEntity entity = firmBankingRequestRepository
                .findByAggregateIdentifier(firmBankingAggregateIdentifier.getFirmBankingAggregateIdentifier())
                .getFirst();
        entity.setFirmBankingStatus(firmBankingStatus.getFirmBankingStatus());
        firmBankingRequestRepository.save(entity);
    }

    private static FirmBankingRequest toDomain(FirmBankingRequestJpaEntity entity) {
        return FirmBankingRequest.generateFirmBankingRequest(
                new FirmBankingRequest.FirmBankingRequestId(entity.getRequestFirmBankingId() + ""),
                new FirmBankingRequest.FromBankName(entity.getFromBankName()),
                new FirmBankingRequest.FromBankAccountNumber(entity.getFromBankAccountNumber()),
                new FirmBankingRequest.ToBankName(entity.getToBankName()),
                new FirmBankingRequest.ToBankAccountNumber(entity.getToBankAccountNumber()),
                new FirmBankingRequest.MoneyAmount(entity.getMoneyAmount()),
                new FirmBankingRequest.FirmBankingStatus(entity.getFirmBankingStatus()),
                UUID.fromString(entity.getUuid()),
                new FirmBankingRequest.FirmBankingAggregateIdentifier(entity.getAggregateIdentifier())
        );
    }

}
