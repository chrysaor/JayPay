package com.jaypay.banking.adapter.out.persistence;

import com.jaypay.banking.domain.FirmBankingRequest;
import org.springframework.stereotype.Component;

@Component
public class FirmBankingRequestMapper {
    public FirmBankingRequest mapToDomainEntity(FirmBankingRequestJpaEntity requestFirmBankingJpaEntity) {
        return FirmBankingRequest.generateFirmBankingRequest(
                new FirmBankingRequest.FirmBankingRequestId(requestFirmBankingJpaEntity.getRequestFirmBankingId()+""),
                new FirmBankingRequest.FromBankName(requestFirmBankingJpaEntity.getFromBankName()),
                new FirmBankingRequest.FromBankAccountNumber(requestFirmBankingJpaEntity.getFromBankAccountNumber()),
                new FirmBankingRequest.ToBankName(requestFirmBankingJpaEntity.getToBankName()),
                new FirmBankingRequest.ToBankAccountNumber(requestFirmBankingJpaEntity.getToBankAccountNumber()),
                new FirmBankingRequest.MoneyAmount(requestFirmBankingJpaEntity.getMoneyAmount()),
                new FirmBankingRequest.FirmBankingStatus(requestFirmBankingJpaEntity.getFirmBankingStatus()),
                requestFirmBankingJpaEntity.getUuid()
        );
    }
}