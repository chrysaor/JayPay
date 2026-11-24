package com.jaypay.banking.adapter.in.web;


import com.jaypay.banking.application.port.in.RequestFirmBankingCommand;
import com.jaypay.banking.application.port.in.RequestFirmBankingUseCase;
import com.jaypay.banking.application.port.in.UpdateFirmBankingCommand;
import com.jaypay.banking.application.port.in.UpdateFirmBankingUseCase;
import com.jaypay.banking.domain.FirmBankingRequest;
import com.jaypay.common.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class RequestFirmBankingController {

    private final RequestFirmBankingUseCase requestFirmBankingUseCase;
    private final UpdateFirmBankingUseCase updateFirmBankingUseCase;

    @PostMapping(path = "/banking/firmbanking/request")
    FirmBankingRequest requestFirmBanking(@RequestBody RequestFirmBankingRequest request) {
        RequestFirmBankingCommand command = RequestFirmBankingCommand.builder()
                .fromBankName(request.getFromBankName())
                .fromBankAccountNumber(request.getFromBankAccountNumber())
                .toBankName(request.getToBankName())
                .toBankAccountNumber(request.getToBankAccountNumber())
                .moneyAmount(request.getMoneyAmount())
                .build();

        return requestFirmBankingUseCase.requestFirmBanking(command);
    }

    @PostMapping(path = "/banking/firmbanking/request-eda")
    void requestFirmBankingByEvent(@RequestBody RequestFirmBankingRequest request) {
        RequestFirmBankingCommand command = RequestFirmBankingCommand.builder()
                .fromBankName(request.getFromBankName())
                .fromBankAccountNumber(request.getFromBankAccountNumber())
                .toBankName(request.getToBankName())
                .toBankAccountNumber(request.getToBankAccountNumber())
                .moneyAmount(request.getMoneyAmount())
                .build();

        requestFirmBankingUseCase.requestFirmBankingByEvent(command);
    }

    @PutMapping(path = "/banking/firmbanking/update-eda")
    void updateFirmBankingByEvent(@RequestBody UpdateFirmBankingRequest request) {
        UpdateFirmBankingCommand command = UpdateFirmBankingCommand.builder()
                .firmBankingAggregateIdentifier(request.getFirmBankingRequestAggregateIdentifier())
                .firmBankingStatus(request.getStatus())
                .build();

        updateFirmBankingUseCase.updateFirmBankingByEvent(command);
    }

}
