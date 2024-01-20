package com.jaypay.banking.application.service;


import com.jaypay.banking.adapter.out.external.bank.BankAccount;
import com.jaypay.banking.adapter.out.external.bank.GetBankAccountRequest;
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
        // Get bank account
        BankAccount bankAccount = requestBankingInfoPort.getBankAccountInfo(
                new GetBankAccountRequest(command.getBankName(), command.getBankAccountNumber())
        );

        // Check bank account is valid
        if (!bankAccount.isValid()) {
            return null;
        }

        return ExternalBankingInfo.generatedExternalBankingInfo(
                new ExternalBankingInfo.BankName(bankAccount.getBankName()),
                new ExternalBankingInfo.BankAccountNumber(bankAccount.getBankAccountNumber()),
                new ExternalBankingInfo.IsValid(bankAccount.isValid())
        );
    }
}
