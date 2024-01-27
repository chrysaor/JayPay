package com.jaypay.banking.application.port.out;

import com.jaypay.banking.adapter.out.persistence.FirmBankingRequestJpaEntity;
import com.jaypay.banking.domain.FirmBankingRequest;

public interface RequestFirmBankingPort {

    FirmBankingRequestJpaEntity createFirmBankingRequest(
            FirmBankingRequest.FromBankName fromBankName,
            FirmBankingRequest.FromBankAccountNumber fromBankAccountNumber,
            FirmBankingRequest.ToBankName toBankName,
            FirmBankingRequest.ToBankAccountNumber toBankAccountNumber,
            FirmBankingRequest.MoneyAmount moneyAmount,
            FirmBankingRequest.FirmBankingStatus firmBankingStatus,
            FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier
    );

    FirmBankingRequestJpaEntity modifyFirmBankingRequest(
            FirmBankingRequestJpaEntity entity
    );

    FirmBankingRequestJpaEntity getFirmBankingRequest(
            FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier
    );

}
