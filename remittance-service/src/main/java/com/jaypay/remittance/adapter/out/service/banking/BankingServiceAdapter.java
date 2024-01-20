package com.jaypay.remittance.adapter.out.service.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.remittance.adapter.out.service.membership.Membership;
import com.jaypay.remittance.application.port.out.banking.BankingInfo;
import com.jaypay.remittance.application.port.out.banking.BankingPort;
import com.jaypay.remittance.application.port.out.membership.MembershipStatus;
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
        String buildUrl = String.join("/", this.bankingServiceEndpoint, "banking", "info");

        try {
            String jsonResponse = bankingServiceHttpClient.sendGetRequest(buildUrl).body();
            ObjectMapper mapper = new ObjectMapper();

            return mapper.readValue(jsonResponse, BankingInfo.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean requestFirmbanking(String bankName, String bankAccountNumber, int amount) {
        return false;
    }

}
