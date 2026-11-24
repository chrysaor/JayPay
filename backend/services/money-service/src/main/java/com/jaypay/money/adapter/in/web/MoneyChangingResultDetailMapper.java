package com.jaypay.money.adapter.in.web;

import com.jaypay.money.domain.MoneyChangingRequest;
import org.springframework.stereotype.Component;

@Component
public class MoneyChangingResultDetailMapper {

    public MoneyChangingResultDetail mapToMoneyChangingResultDetail(MoneyChangingRequest moneyChangingRequest) {
        return new MoneyChangingResultDetail(
                moneyChangingRequest.getMoneyChangingRequestId(),
                0,
                0,
                moneyChangingRequest.getChangingMoneyAmount()
        );
    }

}