package com.jaypay.money.adapter.in.web;


import com.jaypay.common.WebAdapter;
import com.jaypay.money.application.port.in.*;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class RequestMoneyChangingController {

    private final IncreaseMoneyRequestUseCase increaseMoneyRequestUseCase;
    private final CreateMemberMoneyUseCase createMemberMoneyUseCase;
    private final FindMemberMoneyQueryUseCase findMemberMoneyQueryUseCase;

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

    @PostMapping(path = "/money/decrease")
    MoneyChangingResultDetail decreaseMoneyChangingRequest(@RequestBody DecreaseMoneyChangingRequest request) {
        System.out.println(request.toString());
        return null;
    }

    @PostMapping(path = "/money/decrease-eda")
    MoneyChangingResultDetail decreaseMoneyChangingRequestByEvent(@RequestBody DecreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = IncreaseMoneyRequestCommand.builder()
                .targetMembershipId(request.getTargetMembershipId())
                .amount(request.getAmount() * -1)
                .build();

        increaseMoneyRequestUseCase.increaseMoneyRequestByEvent(command);
        return null;
    }

    @PostMapping(path = "/money/create-member-money")
    void createMemberMoney(@RequestBody CreateMemberMoneyRequest request) {
        createMemberMoneyUseCase.createMemberMoney(CreateMemberMoneyCommand.builder()
                .membershipId(request.getMembershipId())
                .build()
        );
    }

    @PostMapping(path = "/money/increase-eda")
    void increaseMoneyChangingRequestByEvent(@RequestBody IncreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = IncreaseMoneyRequestCommand.builder()
                .targetMembershipId(request.getTargetMembershipId())
                .amount(request.getAmount())
                .build();

        increaseMoneyRequestUseCase.increaseMoneyRequestByEvent(command);
    }

    @PostMapping(path = "/money/member-money")
    List<MemberMoney> findMemberMoneyListByMembershipIdsRequest(@RequestBody FindMemberMoneyListByMembershipIdsRequest request) {
        FindMemberMoneyListByMembershipIdsQuery query = FindMemberMoneyListByMembershipIdsQuery.builder()
                .membershipIds(request.getTargetMembershipIds())
                .build();

        return findMemberMoneyQueryUseCase.findMemberMoneyListByMembershipIds(query);
    }

}
