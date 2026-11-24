package com.jaypay.banking.adapter.out.external.bank;

import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.application.port.out.RequestExternalFirmBankingPort;
import com.jaypay.banking.domain.ExternalBankingInfo;
import com.jaypay.banking.domain.FirmBankingTransfer;
import com.jaypay.common.ExternalSystemAdapter;

@ExternalSystemAdapter
public class BankAccountAdapter implements RequestBankAccountInfoPort, RequestExternalFirmBankingPort {

    @Override
    public ExternalBankingInfo getBankAccountInfo(String bankName, String bankAccountNumber) {
        return ExternalBankingInfo.generatedExternalBankingInfo(
                new ExternalBankingInfo.BankName(bankName),
                new ExternalBankingInfo.BankAccountNumber(bankAccountNumber),
                new ExternalBankingInfo.IsValid(true)
        );
    }

    @Override
    public int requestExternalFirmBanking(FirmBankingTransfer transfer) {
        return 0;
    }
}
