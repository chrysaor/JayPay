package com.jaypay.money.query.adapter.out.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.money.query.application.port.out.GetMemberAddressInfoPort;
import com.jaypay.money.query.application.port.out.MemberAddressInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MembershipServiceAdapter implements GetMemberAddressInfoPort {

    private final CommonHttpClient commonHttpClient;

    private final String membershipServiceUrl;

    public MembershipServiceAdapter(CommonHttpClient commonHttpClient,
                                    @Value("${service.membership.url}") String membershipServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.membershipServiceUrl = membershipServiceUrl;
    }

    @Override
    public MemberAddressInfo getMemberAddressInfo(String membershipId) {
        String url = String.join("/", membershipServiceUrl, "memberships", membershipId);
        try {
            String jsonResponse = commonHttpClient.sendGetRequest(url).body();

            ObjectMapper mapper = new ObjectMapper();
            Membership membership = mapper.readValue(jsonResponse, Membership.class);
            return new MemberAddressInfo(membership.getMembershipId(), membership.getAddress());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
