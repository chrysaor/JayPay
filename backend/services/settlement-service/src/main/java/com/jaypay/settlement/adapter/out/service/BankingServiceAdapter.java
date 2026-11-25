package com.jaypay.settlement.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BankingServiceAdapter implements BankingPort {

    private static final String SETTLEMENT_BANK_NAME = "jaypay";
    private static final String SETTLEMENT_BANK_ACCOUNT_NUMBER = "111-222-333";
    private static final int TRANSFERRED = 1;

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
        return Optional.ofNullable(commonHttpClient.get(url, BankAccount.class))
                .filter(account -> account.bankAccountNumber() != null && !account.bankAccountNumber().isBlank());
    }

    @Override
    public boolean requestFirmBanking(BankAccount to, long amount) {
        String url = String.join("/", bankingServiceUrl, "banking/firmbanking/request");
        FirmBankingResult result = commonHttpClient.post(url, new RequestFirmBankingRequest(
                SETTLEMENT_BANK_NAME,
                SETTLEMENT_BANK_ACCOUNT_NUMBER,
                to.bankName(),
                to.bankAccountNumber(),
                amount), FirmBankingResult.class);
        return result != null && result.firmBankingStatus() == TRANSFERRED;
    }

    record RequestFirmBankingRequest(
            String fromBankName,
            String fromBankAccountNumber,
            String toBankName,
            String toBankAccountNumber,
            long moneyAmount) {
    }

    record FirmBankingResult(int firmBankingStatus) {
    }
}
