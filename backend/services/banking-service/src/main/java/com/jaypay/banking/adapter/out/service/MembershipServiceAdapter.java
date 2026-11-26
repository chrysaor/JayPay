package com.jaypay.banking.adapter.out.service;

import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.membership.MembershipResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class MembershipServiceAdapter implements GetMembershipPort {

    private final CommonHttpClient commonHttpClient;
    private final String membershipServiceUrl;

    public MembershipServiceAdapter(CommonHttpClient commonHttpClient,
                                    @Value("${service.membership.url}") String membershipServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.membershipServiceUrl = membershipServiceUrl;
    }

    @Override
    public MembershipStatus getMembership(String membershipId) {
        String url = String.join("/", membershipServiceUrl, "memberships", membershipId);
        MembershipResponse membership = commonHttpClient.get(url, MembershipResponse.class).body();
        return membership.valid() ? new MembershipStatus(membership.membershipId(), true) : null;
    }

}
