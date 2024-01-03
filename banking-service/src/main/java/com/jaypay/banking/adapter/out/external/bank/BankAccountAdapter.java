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
        // Communicate to external Bank services
        // 실제 계좌 정보를 가져오는 구현체
        return new BankAccount(request.getBankName(), request.getBankAccountNumber(), true);
    }

    @Override
    public FirmBankingResult requestExternalFirmBanking(ExternalFirmBankingRequest request) {
        // 외부 은행과 HTTP 통신을 통해 펌뱅킹 요청함
        // 결과를 FirmBankingResult 저장
        return new FirmBankingResult(0);
    }
}
