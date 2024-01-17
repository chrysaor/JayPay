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

    private final CommonHttpClient bankingServiceHttpClient;

    @Value("${service.banking.url}")
    private String bankingServiceEndpoint;

    @Override
    public BankingInfo getMembershipBankingInfo(String bankName, String bankAccountNumber) {
        return null;
    }

    @Override
    public boolean requestFirmbanking(String bankName, String bankAccountNumber, int amount) {
        return false;
    }

}
