package com.jaypay.banking.adapter.out.persistence;

import com.jaypay.banking.application.port.out.RegisterBankAccountPort;
import com.jaypay.banking.application.port.out.RequestFirmBankingPort;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.banking.domain.RegisteredBankAccount;
import com.jaypay.common.PersistenceAdapter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
public class FirmBankingRequestPersistenceAdapter implements RequestFirmBankingPort {

    private final SpringDataFirmBankingRequestRepository firmBankingRequestRepository;

    @Override
    public FirmBankingRequestJpaEntity createFirmBankingRequest(FirmBankingRequest.FromBankName fromBankName, FirmBankingRequest.FromBankAccountNumber fromBankAccountNumber, FirmBankingRequest.ToBankName toBankName, FirmBankingRequest.ToBankAccountNumber toBankAccountNumber, FirmBankingRequest.MoneyAmount moneyAmount, FirmBankingRequest.FirmBankingStatus firmBankingStatus, FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier) {
        return firmBankingRequestRepository.save(new FirmBankingRequestJpaEntity(
                        fromBankName.getFromBankName(),
                        fromBankAccountNumber.getFromBankAccountNumber(),
                        toBankName.getToBankName(),
                        toBankAccountNumber.getToBankAccountNumber(),
                        moneyAmount.getMoneyAmount(),
                        firmBankingStatus.getFirmBankingStatus(),
                        UUID.randomUUID(),
                        firmBankingAggregateIdentifier.getFirmBankingAggregateIdentifier()
                )
        );
    }

    @Override
    public FirmBankingRequestJpaEntity modifyFirmBankingRequest(FirmBankingRequestJpaEntity entity) {
        return firmBankingRequestRepository.save(entity);
    }

    @Override
    public FirmBankingRequestJpaEntity getFirmBankingRequest(FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier) {
        List<FirmBankingRequestJpaEntity> eneityList = firmBankingRequestRepository.findByAggregateIdentifier(firmBankingAggregateIdentifier.getFirmBankingAggregateIdentifier());
        if (!eneityList.isEmpty()) {
            return eneityList.get(0);
        }
        return null;
    }

}
