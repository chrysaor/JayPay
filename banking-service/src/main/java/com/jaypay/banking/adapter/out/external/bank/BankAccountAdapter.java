package com.jaypay.banking.adapter.out.external.bank;

import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.common.ExternalSystemAdapter;
import lombok.RequiredArgsConstructor;

@ExternalSystemAdapter
@RequiredArgsConstructor
public class BankAccountAdapter implements RequestBankAccountInfoPort {

    @Override
    public BankAccount getBankAccountInfo(GetBankAccountRequest request) {
        // Communicate to external Bank services
        // 실제 계좌 정보를 가져오는 구현체
        return new BankAccount(request.getBankName(), request.getBankAccountNumber(), true);
    }

}
