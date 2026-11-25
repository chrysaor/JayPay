package com.jaypay.payment.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.payment.application.port.out.DecreaseMoneyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MoneyServiceAdapter implements DecreaseMoneyPort {

    private static final int SUCCEEDED = 1;
    private static final int FAILED = 2;

    private final CommonHttpClient commonHttpClient;
    private final String moneyServiceUrl;

    public MoneyServiceAdapter(CommonHttpClient commonHttpClient,
                               @Value("${service.money.url}") String moneyServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.moneyServiceUrl = moneyServiceUrl;
    }

    @Override
    public Debit decreaseMoney(String requestId, String membershipId, long amount) {
        try {
            MoneyChangingResult result = commonHttpClient.post(
                    String.join("/", moneyServiceUrl, "money", "decrease"),
                    new MoneyChangingRequest(requestId, membershipId, amount),
                    MoneyChangingResult.class).body();
            if (result != null && requestId.equals(result.moneyChangingRequestId())) {
                if (result.moneyChangingResultStatus() == SUCCEEDED) {
                    return Debit.SUCCEEDED;
                }
                if (result.moneyChangingResultStatus() == FAILED) {
                    return Debit.FAILED;
                }
            }
            log.warn("money-service did not confirm the debit {}: {}", requestId, result);
        } catch (RuntimeException e) {
            log.warn("money-service did not answer the debit {}", requestId, e);
        }
        return Debit.UNCONFIRMED;
    }

    record MoneyChangingRequest(String requestId, String targetMembershipId, long amount) {
    }

    record MoneyChangingResult(String moneyChangingRequestId, int moneyChangingResultStatus) {
    }
}
