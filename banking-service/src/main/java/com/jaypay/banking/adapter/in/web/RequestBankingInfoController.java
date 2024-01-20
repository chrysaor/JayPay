package com.jaypay.banking.adapter.in.web;

import com.jaypay.banking.application.port.in.RequestBankingInfoCommand;
import com.jaypay.banking.application.port.in.RequestBankingInfoUseCase;
import com.jaypay.banking.domain.ExternalBankingInfo;
import com.jaypay.common.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@WebAdapter
@RestController
@RequiredArgsConstructor
public class RequestBankingInfoController {

    private final RequestBankingInfoUseCase requestBankingInfoUseCase;

    @PostMapping(path = "/banking/info")
    ExternalBankingInfo requestExternalBankingInfo(@RequestBody RequestBankingInfoRequest request) {
        RequestBankingInfoCommand command = RequestBankingInfoCommand.builder()
                .bankName(request.getBankName())
                .bankAccountNumber(request.getBankAccountNumber())
                .build();

        return requestBankingInfoUseCase.requestExternalBankingInfo(command);
    }

}
