package com.jaypay.money.aggregation.adapter.out.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.HttpReply;
import com.jaypay.money.aggregation.application.port.out.GetMoneySumPort;
import com.jaypay.money.aggregation.application.port.out.MemberMoney;
import com.jaypay.money.aggregation.domain.MoneySumNotConfirmedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.UncheckedIOException;
import java.util.List;

@ExternalSystemAdapter
@Slf4j
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
        HttpReply<List<MemberMoney>> reply;
        try {
            reply = commonHttpClient.post(url, new FindMemberMoneyRequest(membershipIds), new TypeReference<>() {});
        } catch (UncheckedIOException unanswered) {
            log.warn("money-service did not answer the balances of members", unanswered);
            throw new MoneySumNotConfirmedException("money-service", unanswered.getMessage());
        }
        if (reply.body() == null) {
            log.warn("money-service did not answer the balances of members: status {}", reply.status());
            throw new MoneySumNotConfirmedException("money-service", "status " + reply.status());
        }
        return reply.body();
    }

    record FindMemberMoneyRequest(List<String> targetMembershipIds) {
    }
}
