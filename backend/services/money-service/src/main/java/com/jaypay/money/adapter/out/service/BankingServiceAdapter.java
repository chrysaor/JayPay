package com.jaypay.money.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.money.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.money.application.port.out.RegisteredBankAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class BankingServiceAdapter implements GetRegisteredBankAccountPort {

    private final CommonHttpClient commonHttpClient;

    private final String bankingServiceUrl;

    public BankingServiceAdapter(CommonHttpClient commonHttpClient,
                                 @Value("${service.banking.url}") String bankingServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.bankingServiceUrl = bankingServiceUrl;
    }

    @Override
    public RegisteredBankAccount getRegisteredBankAccount(String membershipId) {
        String url = String.join("/", bankingServiceUrl, "banking/account", membershipId);
        return commonHttpClient.get(url, RegisteredBankAccount.class);
    }

}
