package com.jaypay.banking.application.port.out;

import com.jaypay.banking.adapter.out.external.bank.BankAccount;
import com.jaypay.banking.adapter.out.external.bank.GetBankAccountRequest;

public interface RequestBankAccountInfoPort {
    BankAccount getBankAccountInfo(GetBankAccountRequest request) ;
}
