package com.jaypay.remittance.adapter.out.service.money;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.remittance.application.port.out.money.MoneyInfo;
import com.jaypay.remittance.application.port.out.money.MoneyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;


@ExternalSystemAdapter
@RequiredArgsConstructor
public class MoneyServiceAdapter implements MoneyPort {

    private static final int MONEY_CHANGING_SUCCEEDED = 1;

    private final CommonHttpClient moneyServiceHttpClient;

    @Value("${service.money.url}")
    private String moneyServiceEndpoint;

    @Override
    public MoneyInfo getMoneyInfo(String membershipId) {
        List<MemberMoney> wallets = moneyServiceHttpClient.post(
                url("member-money"),
                new MemberMoneyRequest(List.of(membershipId)),
                new TypeReference<List<MemberMoney>>() {}
        );
        int balance = wallets == null || wallets.isEmpty() ? 0 : wallets.getFirst().balance();
        return new MoneyInfo(membershipId, balance);
    }

    @Override
    public boolean requestMoneyRecharging(String membershipId, int amount) {
        return changeMoney(membershipId, amount);
    }

    @Override
    public boolean requestMoneyIncrease(String membershipId, int amount) {
        return changeMoney(membershipId, amount);
    }

    @Override
    public boolean requestMoneyDecrease(String membershipId, int amount) {
        return changeMoney(membershipId, -amount);
    }

    private boolean changeMoney(String membershipId, int amount) {
        MoneyChangingResult result = moneyServiceHttpClient.post(
                url("increase"),
                new MoneyChangingRequest(membershipId, amount),
                MoneyChangingResult.class
        );
        return result != null && result.moneyChangingResultStatus() == MONEY_CHANGING_SUCCEEDED;
    }

    private String url(String path) {
        return String.join("/", moneyServiceEndpoint, "money", path);
    }

    record MemberMoneyRequest(List<String> targetMembershipIds) {
    }

    record MemberMoney(String membershipId, int balance) {
    }

    record MoneyChangingRequest(String targetMembershipId, int amount) {
    }

    record MoneyChangingResult(int moneyChangingResultStatus) {
    }

}
