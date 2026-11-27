package com.jaypay.money.adapter.in.web;


import com.jaypay.common.WebAdapter;
import com.jaypay.money.application.port.in.*;
import com.jaypay.money.domain.MemberMoney;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class RequestMoneyChangingController {

    private final MoneyChangingCommands moneyChangingCommands;
    private final ChangeMoneyUseCase changeMoneyUseCase;
    private final CreateMemberMoneyUseCase createMemberMoneyUseCase;
    private final FindMemberMoneyQueryUseCase findMemberMoneyQueryUseCase;

    @PostMapping(path = "/money/increase")
    MoneyChangingResultDetail increaseMoneyChangingRequest(@RequestBody MoneyChangingRequestBody request) {
        return MoneyChangingResultDetail.of(changeMoneyUseCase.increaseMoney(moneyChangingCommands.parse(request)));
    }

    @PostMapping(path = "/money/decrease")
    MoneyChangingResultDetail decreaseMoneyChangingRequest(@RequestBody MoneyChangingRequestBody request) {
        return MoneyChangingResultDetail.of(changeMoneyUseCase.decreaseMoney(moneyChangingCommands.parse(request)));
    }

    @PostMapping(path = "/money/refund")
    MoneyChangingResultDetail refundMoneyChangingRequest(@RequestBody RefundRequestBody request) {
        return MoneyChangingResultDetail.of(changeMoneyUseCase.refundMoney(moneyChangingCommands.parse(request)));
    }

    @PostMapping(path = "/money/create-member-money")
    void createMemberMoney(@RequestBody CreateMemberMoneyRequest request) {
        createMemberMoneyUseCase.createMemberMoney(new CreateMemberMoneyCommand(request.getMembershipId()));
    }

    @PostMapping(path = "/money/increase-eda")
    MoneyChangingResultDetail increaseMoneyChangingRequestByEvent(@RequestBody MoneyChangingRequestBody request) {
        return MoneyChangingResultDetail.of(changeMoneyUseCase.increaseMoneyRequestByEvent(moneyChangingCommands.parse(request)));
    }

    @PostMapping(path = "/money/member-money")
    List<MemberMoney> findMemberMoneyListByMembershipIdsRequest(@RequestBody FindMemberMoneyListByMembershipIdsRequest request) {
        FindMemberMoneyListByMembershipIdsQuery query = new FindMemberMoneyListByMembershipIdsQuery(request.getTargetMembershipIds());

        return findMemberMoneyQueryUseCase.findMemberMoneyListByMembershipIds(query);
    }

}
