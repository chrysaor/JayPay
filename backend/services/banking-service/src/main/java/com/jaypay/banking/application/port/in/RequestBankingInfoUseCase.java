package com.jaypay.banking.application.port.in;

import com.jaypay.banking.domain.ExternalBankingInfo;

public interface RequestBankingInfoUseCase {

    ExternalBankingInfo requestExternalBankingInfo(RequestBankingInfoCommand command);

}
