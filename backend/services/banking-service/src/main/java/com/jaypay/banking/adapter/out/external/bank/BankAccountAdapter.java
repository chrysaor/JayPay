package com.jaypay.banking.adapter.out.external.bank;

import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.application.port.out.RequestExternalFirmBankingPort;
import com.jaypay.common.ExternalSystemAdapter;
import lombok.RequiredArgsConstructor;

@ExternalSystemAdapter
@RequiredArgsConstructor
public class BankAccountAdapter implements RequestBankAccountInfoPort, RequestExternalFirmBankingPort {

    @Override
    public BankAccount getBankAccountInfo(GetBankAccountRequest request) {
        return new BankAccount(request.getBankName(), request.getBankAccountNumber(), true);
    }

    @Override
    public FirmBankingResult requestExternalFirmBanking(ExternalFirmBankingRequest request) {
        return new FirmBankingResult(0);
    }
}
