package com.jaypay.remittance.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.remittance.application.port.out.MembershipPort;
import org.springframework.beans.factory.annotation.Value;

@ExternalSystemAdapter
public class MembershipServiceAdapter implements MembershipPort {

    private final CommonHttpClient commonHttpClient;
    private final String membershipServiceUrl;

    public MembershipServiceAdapter(CommonHttpClient commonHttpClient, @Value("${service.membership.url}") String membershipServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.membershipServiceUrl = membershipServiceUrl;
    }

    @Override
    public boolean isValid(String membershipId) {
        MembershipResponse membership = commonHttpClient.get(
                String.join("/", membershipServiceUrl, "memberships", membershipId), MembershipResponse.class);
        return membership != null && membershipId.equals(membership.membershipId()) && membership.valid();
    }
}
