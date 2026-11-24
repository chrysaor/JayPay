package com.jaypay.remittance.adapter.out.service.banking;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.remittance.application.port.out.banking.BankingInfo;
import com.jaypay.remittance.application.port.out.banking.BankingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;


@ExternalSystemAdapter
@RequiredArgsConstructor
public class BankingServiceAdapter implements BankingPort {

    private static final String JAYPAY_BANK_NAME = "jaypay-company";
    private static final String JAYPAY_BANK_ACCOUNT_NUMBER = "123456789";
    private static final int FIRM_BANKING_SUCCEEDED = 1;

    private final CommonHttpClient bankingServiceHttpClient;

    @Value("${service.banking.url}")
    private String bankingServiceEndpoint;

    @Override
    public BankingInfo getMembershipBankingInfo(String bankName, String bankAccountNumber) {
        BankingInfo bankingInfo = bankingServiceHttpClient.post(
                url("info"),
                new BankingInfoRequest(bankName, bankAccountNumber),
                BankingInfo.class
        );
        return bankingInfo == null ? new BankingInfo(bankName, bankAccountNumber, false) : bankingInfo;
    }

    @Override
    public boolean requestFirmbanking(String bankName, String bankAccountNumber, int amount) {
        FirmBankingResult result = bankingServiceHttpClient.post(
                url("firmbanking", "request"),
                new FirmBankingRequest(JAYPAY_BANK_NAME, JAYPAY_BANK_ACCOUNT_NUMBER, bankName, bankAccountNumber, amount),
                FirmBankingResult.class
        );
        return result != null && result.firmBankingStatus() == FIRM_BANKING_SUCCEEDED;
    }

    private String url(String... path) {
        return String.join("/", bankingServiceEndpoint, "banking", String.join("/", path));
    }

    record BankingInfoRequest(String bankName, String bankAccountNumber) {
    }

    record FirmBankingRequest(String fromBankName, String fromBankAccountNumber, String toBankName, String toBankAccountNumber, int moneyAmount) {
    }

    record FirmBankingResult(int firmBankingStatus) {
    }

}
