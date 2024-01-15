package com.jaypay.money.adapter.in.web;


import com.jaypay.common.WebAdapter;
import com.jaypay.money.application.port.in.IncreaseMoneyRequestCommand;
import com.jaypay.money.application.port.in.IncreaseMoneyRequestUseCase;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class RequestMoneyChangingController {

    private final IncreaseMoneyRequestUseCase increaseMoneyRequestUseCase;

    @PostMapping(path = "/money/increase")
    MoneyChangingResultDetail increaseMoneyChangingRequest(@RequestBody IncreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = IncreaseMoneyRequestCommand.builder()
                .targetMembershipId(request.getTargetMembershipId())
                .amount(request.getAmount())
                .build();

        MoneyChangingRequest moneyChangingRequest = increaseMoneyRequestUseCase.increaseMoneyRequest(command);

        // moneyChangingRequest -> MoneyCHangingResultDetail

        return new MoneyChangingResultDetail(
                moneyChangingRequest.getMoneyChangingRequestId(),
                1,
                moneyChangingRequest.getMoneyChangingStatus(),
                moneyChangingRequest.getChangingMoneyAmount()
        );
    }

    @PostMapping(path = "/money/decrease")
    MoneyChangingResultDetail decreaseMoneyChangingRequest(@RequestBody DecreaseMoneyChangingRequest request) {
        System.out.println(request.toString());
        return null;
    }


    @PostMapping(path = "/money/increase-async")
    MoneyChangingResultDetail increaseMoneyChangingRequestAsync(@RequestBody IncreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = IncreaseMoneyRequestCommand.builder()
                .targetMembershipId(request.getTargetMembershipId())
                .amount(request.getAmount())
                .build();

        MoneyChangingRequest moneyChangingRequest = increaseMoneyRequestUseCase.increaseMoneyRequestAsync(command);

        // moneyChangingRequest -> MoneyCHangingResultDetail

        return new MoneyChangingResultDetail(
                moneyChangingRequest.getMoneyChangingRequestId(),
                1,
                moneyChangingRequest.getMoneyChangingStatus(),
                moneyChangingRequest.getChangingMoneyAmount()
        );
    }

}
