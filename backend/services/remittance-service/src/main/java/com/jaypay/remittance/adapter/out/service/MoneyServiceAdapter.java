package com.jaypay.remittance.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.remittance.application.port.out.Outcome;
import com.jaypay.remittance.application.port.out.WalletPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@ExternalSystemAdapter
@Slf4j
public class MoneyServiceAdapter implements WalletPort {

    private static final int SUCCEEDED = 1;
    private static final int FAILED = 2;

    private final CommonHttpClient commonHttpClient;
    private final String moneyServiceUrl;

    public MoneyServiceAdapter(CommonHttpClient commonHttpClient, @Value("${service.money.url}") String moneyServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.moneyServiceUrl = moneyServiceUrl;
    }

    @Override
    public Outcome decrease(String requestId, String membershipId, long amount) {
        return change("decrease", requestId, membershipId, amount);
    }

    @Override
    public Outcome increase(String requestId, String membershipId, long amount) {
        return change("increase", requestId, membershipId, amount);
    }

    private Outcome change(String direction, String requestId, String membershipId, long amount) {
        try {
            MoneyChangingResult result = commonHttpClient.post(
                    String.join("/", moneyServiceUrl, "money", direction),
                    new MoneyChangingRequest(requestId, membershipId, amount),
                    MoneyChangingResult.class);
            if (result != null && requestId.equals(result.moneyChangingRequestId())) {
                if (result.moneyChangingResultStatus() == SUCCEEDED) {
                    return Outcome.SUCCEEDED;
                }
                if (result.moneyChangingResultStatus() == FAILED) {
                    return Outcome.FAILED;
                }
            }
            log.warn("money-service did not confirm the change {}: {}", requestId, result);
        } catch (RuntimeException e) {
            log.warn("money-service did not answer the change {}", requestId, e);
        }
        return Outcome.UNCONFIRMED;
    }

    record MoneyChangingRequest(String requestId, String targetMembershipId, long amount) {
    }

    record MoneyChangingResult(String moneyChangingRequestId, int moneyChangingResultStatus) {
    }
}
