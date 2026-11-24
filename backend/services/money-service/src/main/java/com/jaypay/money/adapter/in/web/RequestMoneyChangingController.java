package com.jaypay.money.adapter.in.web;


import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RequestMoneyChangingController {

    private final IncreaseMoneyRequestUseCase increaseMoneyRequestUseCase;
    private final CreateMemberMoneyUseCase createMemberMoneyUseCase;
    private final FindMemberMoneyQueryUseCase findMemberMoneyQueryUseCase;

    @PostMapping(path = "/money/increase")
    MoneyChangingResultDetail increaseMoneyChangingRequest(@RequestBody IncreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = new IncreaseMoneyRequestCommand(request.getTargetMembershipId(), request.getAmount());

        MoneyChangingRequest moneyChangingRequest = increaseMoneyRequestUseCase.increaseMoneyRequest(command);

        return new MoneyChangingResultDetail(
                moneyChangingRequest.getMoneyChangingRequestId(),
                1,
                moneyChangingRequest.getMoneyChangingStatus(),
                moneyChangingRequest.getChangingMoneyAmount()
        );
    }

    @PostMapping(path = "/money/increase-async")
    MoneyChangingResultDetail increaseMoneyChangingRequestAsync(@RequestBody IncreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = new IncreaseMoneyRequestCommand(request.getTargetMembershipId(), request.getAmount());

        MoneyChangingRequest moneyChangingRequest = increaseMoneyRequestUseCase.increaseMoneyRequestAsync(command);

        return new MoneyChangingResultDetail(
                moneyChangingRequest.getMoneyChangingRequestId(),
                1,
                moneyChangingRequest.getMoneyChangingStatus(),
                moneyChangingRequest.getChangingMoneyAmount()
        );
    }

    @PostMapping(path = "/money/decrease")
    MoneyChangingResultDetail decreaseMoneyChangingRequest(@RequestBody DecreaseMoneyChangingRequest request) {
        log.info("{}", request);
        return null;
    }

    @PostMapping(path = "/money/decrease-eda")
    MoneyChangingResultDetail decreaseMoneyChangingRequestByEvent(@RequestBody DecreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = new IncreaseMoneyRequestCommand(request.getTargetMembershipId(), request.getAmount() * -1);

        increaseMoneyRequestUseCase.increaseMoneyRequestByEvent(command);
        return null;
    }

    @PostMapping(path = "/money/create-member-money")
    void createMemberMoney(@RequestBody CreateMemberMoneyRequest request) {
        createMemberMoneyUseCase.createMemberMoney(new CreateMemberMoneyCommand(request.getMembershipId()));
    }

    @PostMapping(path = "/money/increase-eda")
    void increaseMoneyChangingRequestByEvent(@RequestBody IncreaseMoneyChangingRequest request) {
        IncreaseMoneyRequestCommand command = new IncreaseMoneyRequestCommand(request.getTargetMembershipId(), request.getAmount());

        increaseMoneyRequestUseCase.increaseMoneyRequestByEvent(command);
    }

    @PostMapping(path = "/money/member-money")
    List<MemberMoney> findMemberMoneyListByMembershipIdsRequest(@RequestBody FindMemberMoneyListByMembershipIdsRequest request) {
        FindMemberMoneyListByMembershipIdsQuery query = new FindMemberMoneyListByMembershipIdsQuery(request.getTargetMembershipIds());

        return findMemberMoneyQueryUseCase.findMemberMoneyListByMembershipIds(query);
    }

}
