package com.jaypay.banking.application.port.out;

import com.jaypay.banking.domain.ExternalBankingInfo;

public interface RequestBankAccountInfoPort {

    ExternalBankingInfo getBankAccountInfo(String bankName, String bankAccountNumber);

}
