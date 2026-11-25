package com.jaypay.money.aggregation.adapter.out.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.money.aggregation.application.port.out.GetMoneySumPort;
import com.jaypay.money.aggregation.application.port.out.MemberMoney;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@ExternalSystemAdapter
public class MoneyServiceAdapter implements GetMoneySumPort {

    private final CommonHttpClient commonHttpClient;
    private final String moneyServiceUrl;

    public MoneyServiceAdapter(CommonHttpClient commonHttpClient,
                               @Value("${service.money.url}") String moneyServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.moneyServiceUrl = moneyServiceUrl;
    }

    @Override
    public List<MemberMoney> getMoneySumByMembershipIds(List<String> membershipIds) {
        String url = String.join("/", moneyServiceUrl, "money/member-money");
        return commonHttpClient.post(url, new FindMemberMoneyRequest(membershipIds), new TypeReference<>() {});
    }

    record FindMemberMoneyRequest(List<String> targetMembershipIds) {
    }
}
