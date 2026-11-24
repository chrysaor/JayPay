package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.banking.domain.FirmBankingTransfer;

public interface RequestFirmBankingPort {

    FirmBankingRequest createFirmBankingRequest(
            FirmBankingTransfer transfer,
            FirmBankingRequest.FirmBankingStatus firmBankingStatus,
            FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier
    );

    void updateFirmBankingStatus(
            FirmBankingRequest.FirmBankingAggregateIdentifier firmBankingAggregateIdentifier,
            FirmBankingRequest.FirmBankingStatus firmBankingStatus
    );

}
