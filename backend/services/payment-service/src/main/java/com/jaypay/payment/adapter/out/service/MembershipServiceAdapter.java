package com.jaypay.payment.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.payment.application.port.out.GetMembershipPort;
import com.jaypay.payment.application.port.out.MembershipStatus;
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
        MembershipResponse membership = commonHttpClient.get(url, MembershipResponse.class);
        boolean found = membership != null && membershipId.equals(membership.membershipId());
        return new MembershipStatus(membershipId, found && membership.valid(), found && membership.corp());
    }
}
