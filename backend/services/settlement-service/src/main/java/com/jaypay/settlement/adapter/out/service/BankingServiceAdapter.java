package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.HttpReply;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payout;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
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
    public Optional<BankAccount> getRegisteredBankAccount(String membershipId) {
        String url = String.join("/", bankingServiceUrl, "banking/account", membershipId);
        return Optional.ofNullable(commonHttpClient.get(url, BankAccount.class).body())
                .filter(account -> account.bankAccountNumber() != null && !account.bankAccountNumber().isBlank());
    }

    @Override
    public Payout pay(String requestId, BankAccount to, long amount) {
        String url = String.join("/", bankingServiceUrl, "banking/firmbanking/request");
        try {
            HttpReply<TransferResult> reply = commonHttpClient.post(url, new TransferRequest(
                    requestId,
                    SETTLEMENT_BANK_NAME,
                    SETTLEMENT_BANK_ACCOUNT_NUMBER,
                    to.bankName(),
                    to.bankAccountNumber(),
                    amount), TransferResult.class);
            TransferResult result = reply.body();
            if (reply.isSuccessful() && result != null && requestId.equals(result.requestId())) {
                if ("SUCCEEDED".equals(result.transferStatus())) {
                    return Payout.PAID;
                }
                if ("FAILED".equals(result.transferStatus())) {
                    return Payout.REFUSED;
                }
            }
            if (reply.status() == 400) {
                return Payout.REFUSED;
            }
            log.warn("banking-service did not confirm the payout {}: status {}", requestId, reply.status());
        } catch (RuntimeException e) {
            log.warn("banking-service did not answer the payout {}", requestId, e);
        }
        return Payout.UNCONFIRMED;
    }

    record TransferRequest(
            String requestId,
            String fromBankName,
            String fromBankAccountNumber,
            String toBankName,
            String toBankAccountNumber,
            long moneyAmount) {
    }

    record TransferResult(String requestId, String transferStatus) {
    }
}
