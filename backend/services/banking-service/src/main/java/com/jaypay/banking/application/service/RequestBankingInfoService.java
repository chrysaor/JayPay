package com.jaypay.banking.application.service;

import com.jaypay.banking.application.port.in.RequestBankingInfoCommand;
import com.jaypay.banking.application.port.in.RequestBankingInfoUseCase;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.domain.ExternalBankingInfo;
import com.jaypay.common.UseCase;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class RequestBankingInfoService implements RequestBankingInfoUseCase {

    private final RequestBankAccountInfoPort requestBankingInfoPort;

    @Override
    public ExternalBankingInfo requestExternalBankingInfo(RequestBankingInfoCommand command) {
        ExternalBankingInfo bankAccount = requestBankingInfoPort.getBankAccountInfo(command.bankName(), command.bankAccountNumber());
        return bankAccount.isValid() ? bankAccount : null;
    }
}
