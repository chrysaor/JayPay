package com.jaypay.banking.application.port.in;


import com.jaypay.banking.domain.FirmBankingRequest;

public interface RequestFirmBankingUseCase {
    FirmBankingRequest requestFirmBanking(RequestFirmBankingCommand command);
    void requestFirmBankingByEvent(RequestFirmBankingCommand command);

}