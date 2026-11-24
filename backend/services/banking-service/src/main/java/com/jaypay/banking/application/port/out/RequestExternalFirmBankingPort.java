package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.FirmBankingTransfer;

public interface RequestExternalFirmBankingPort {

    int requestExternalFirmBanking(FirmBankingTransfer transfer);

}
