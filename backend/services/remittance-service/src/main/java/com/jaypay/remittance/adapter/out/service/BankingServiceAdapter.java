package com.jaypay.remittance.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.HttpReply;
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

    private final CommonHttpClient commonHttpClient;
    private final String bankingServiceUrl;

    public BankingServiceAdapter(CommonHttpClient commonHttpClient, @Value("${service.banking.url}") String bankingServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.bankingServiceUrl = bankingServiceUrl;
    }

    @Override
    public boolean isValid(Receiver.BankAccount account) {
        BankingInfo info = commonHttpClient.post(url("info"),
                new BankingInfoRequest(account.bankName(), account.bankAccountNumber()), BankingInfo.class).body();
        return info != null && info.valid();
    }

    @Override
    public Outcome transfer(String requestId, Receiver.BankAccount account, long amount) {
        try {
            HttpReply<TransferResult> reply = commonHttpClient.post(url("firmbanking", "request"),
                    new TransferRequest(requestId, JAYPAY_BANK_NAME, JAYPAY_BANK_ACCOUNT_NUMBER, account.bankName(), account.bankAccountNumber(), amount),
                    TransferResult.class);
            TransferResult result = reply.body();
            if (reply.isSuccessful() && result != null && requestId.equals(result.requestId())) {
                if ("SUCCEEDED".equals(result.transferStatus())) {
                    return Outcome.SUCCEEDED;
                }
                if ("FAILED".equals(result.transferStatus())) {
                    return Outcome.FAILED;
                }
            }
            if (reply.status() == 400) {
                log.warn("banking-service refused the transfer {} as invalid", requestId);
                return Outcome.FAILED;
            }
            log.warn("banking-service did not confirm the transfer {}: status {}", requestId, reply.status());
        } catch (RuntimeException e) {
            log.warn("banking-service did not answer the transfer {}", requestId, e);
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

    record TransferRequest(String requestId, String fromBankName, String fromBankAccountNumber, String toBankName, String toBankAccountNumber,
                           long moneyAmount) {
    }

    record TransferResult(String requestId, String transferStatus) {
    }
}
