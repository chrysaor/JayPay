package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BankingServiceAdapter implements BankingPort {

    private static final String SETTLEMENT_BANK_NAME = "jaypay";
    private static final String SETTLEMENT_BANK_ACCOUNT_NUMBER = "111-222-333";

    private final CommonHttpClient commonHttpClient;
    private final String bankingServiceUrl;

    public BankingServiceAdapter(CommonHttpClient commonHttpClient,
                                 @Value("${service.banking.url}") String bankingServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.bankingServiceUrl = bankingServiceUrl;
    }

    @Override
    public BankAccount getRegisteredBankAccount(String membershipId) {
        String url = String.join("/", bankingServiceUrl, "banking/account", membershipId);
        return commonHttpClient.get(url, BankAccount.class);
    }

    @Override
    public void requestFirmBanking(BankAccount to, int moneyAmount) {
        String url = String.join("/", bankingServiceUrl, "banking/firmbanking/request");
        commonHttpClient.post(url, new RequestFirmBankingRequest(
                SETTLEMENT_BANK_NAME,
                SETTLEMENT_BANK_ACCOUNT_NUMBER,
                to.bankName(),
                to.bankAccountNumber(),
                moneyAmount));
    }

    record RequestFirmBankingRequest(
            String fromBankName,
            String fromBankAccountNumber,
            String toBankName,
            String toBankAccountNumber,
            int moneyAmount) {
    }
}
