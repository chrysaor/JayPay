package com.jaypay.banking.application.port.out;

import com.jaypay.banking.adapter.out.external.bank.ExternalFirmBankingRequest;
import com.jaypay.banking.adapter.out.external.bank.FirmBankingResult;

public interface RequestExternalFirmBankingPort {

    FirmBankingResult requestExternalFirmBanking(ExternalFirmBankingRequest request);

}
