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
        long balance = wallets == null || wallets.isEmpty() ? 0 : wallets.getFirst().balance();
        return new MoneyInfo(membershipId, balance);
    }

    @Override
    public boolean requestMoneyRecharging(String requestId, String membershipId, long amount) {
        return changeMoney("increase", requestId, membershipId, amount);
    }

    @Override
    public boolean requestMoneyIncrease(String requestId, String membershipId, long amount) {
        return changeMoney("increase", requestId, membershipId, amount);
    }

    @Override
    public boolean requestMoneyDecrease(String requestId, String membershipId, long amount) {
        return changeMoney("decrease", requestId, membershipId, amount);
    }

    private boolean changeMoney(String path, String requestId, String membershipId, long amount) {
        MoneyChangingResult result = moneyServiceHttpClient.post(
                url(path),
                new MoneyChangingRequest(requestId, membershipId, amount),
                MoneyChangingResult.class
        );
        return result != null && result.moneyChangingResultStatus() == MONEY_CHANGING_SUCCEEDED;
    }

    private String url(String path) {
        return String.join("/", moneyServiceEndpoint, "money", path);
    }

    record MemberMoneyRequest(List<String> targetMembershipIds) {
    }

    record MemberMoney(String membershipId, long balance) {
    }

    record MoneyChangingRequest(String requestId, String targetMembershipId, long amount) {
    }

    record MoneyChangingResult(int moneyChangingResultStatus) {
    }

}
