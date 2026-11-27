package com.jaypay.money.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.HttpReply;
import com.jaypay.money.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.money.application.port.out.RegisteredBankAccount;
import com.jaypay.money.domain.RegisteredAccountNotConfirmedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.util.Optional;


@Component
@Slf4j
public class BankingServiceAdapter implements GetRegisteredBankAccountPort {

    private final CommonHttpClient commonHttpClient;

    private final String bankingServiceUrl;

    public BankingServiceAdapter(CommonHttpClient commonHttpClient,
                                 @Value("${service.banking.url}") String bankingServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.bankingServiceUrl = bankingServiceUrl;
    }

    @Override
    public Optional<RegisteredBankAccount> getRegisteredBankAccount(String membershipId) {
        String url = String.join("/", bankingServiceUrl, "banking/account", membershipId);
        HttpReply<RegisteredBankAccount> reply;
        try {
            reply = commonHttpClient.get(url, RegisteredBankAccount.class);
        } catch (UncheckedIOException unanswered) {
            log.warn("banking-service did not answer about the registered account of member {}", membershipId, unanswered);
            throw new RegisteredAccountNotConfirmedException(membershipId, unanswered.getMessage());
        }
        if (reply.status() == HttpURLConnection.HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        if (reply.body() == null) {
            log.warn("banking-service did not confirm the registered account of member {}: status {}", membershipId, reply.status());
            throw new RegisteredAccountNotConfirmedException(membershipId, "status " + reply.status());
        }
        return Optional.of(reply.body());
    }

}
