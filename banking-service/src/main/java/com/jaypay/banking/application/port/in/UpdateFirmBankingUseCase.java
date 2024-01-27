package com.jaypay.banking.application.port.in;


public interface UpdateFirmBankingUseCase {

    void updateFirmBankingByEvent(UpdateFirmBankingCommand command);

}