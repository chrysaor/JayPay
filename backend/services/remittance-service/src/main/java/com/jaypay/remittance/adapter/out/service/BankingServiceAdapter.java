package com.jaypay.remittance.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.remittance.application.port.out.BankingPort;
import com.jaypay.remittance.application.port.out.Outcome;
import com.jaypay.remittance.domain.Receiver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

@ExternalSystemAdapter
@Slf4j
public class BankingServiceAdapter implements BankingPort {

    private static final String JAYPAY_BANK_NAME = "jaypay-company";
    private static final String JAYPAY_BANK_ACCOUNT_NUMBER = "123456789";
    private static final int SUCCEEDED = 1;
    private static final int FAILED = 2;

    private final CommonHttpClient commonHttpClient;
    private final String bankingServiceUrl;

    public BankingServiceAdapter(CommonHttpClient commonHttpClient, @Value("${service.banking.url}") String bankingServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.bankingServiceUrl = bankingServiceUrl;
    }

    @Override
    public boolean isValid(Receiver.BankAccount account) {
        BankingInfo info = commonHttpClient.post(url("info"),
                new BankingInfoRequest(account.bankName(), account.bankAccountNumber()), BankingInfo.class);
        return info != null && info.valid();
    }

    @Override
    public Outcome transfer(Receiver.BankAccount account, long amount) {
        try {
            FirmBankingResult result = commonHttpClient.post(url("firmbanking", "request"),
                    new FirmBankingRequest(JAYPAY_BANK_NAME, JAYPAY_BANK_ACCOUNT_NUMBER, account.bankName(), account.bankAccountNumber(),
                            Math.toIntExact(amount)),
                    FirmBankingResult.class);
            if (result != null && result.firmBankingStatus() == SUCCEEDED) {
                return Outcome.SUCCEEDED;
            }
            if (result != null && result.firmBankingStatus() == FAILED) {
                return Outcome.FAILED;
            }
            log.warn("banking-service did not confirm the transfer to {}: {}", account.bankName(), result);
        } catch (RuntimeException e) {
            log.warn("banking-service did not answer the transfer to {}", account.bankName(), e);
        }
        return Outcome.UNCONFIRMED;
    }

    private String url(String... path) {
        return String.join("/", bankingServiceUrl, "banking", String.join("/", path));
    }

    record BankingInfoRequest(String bankName, String bankAccountNumber) {
    }

    record BankingInfo(boolean valid) {
    }

    record FirmBankingRequest(String fromBankName, String fromBankAccountNumber, String toBankName, String toBankAccountNumber, int moneyAmount) {
    }

    record FirmBankingResult(int firmBankingStatus) {
    }
}
