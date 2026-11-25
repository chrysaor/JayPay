package com.jaypay.money.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.money.application.port.out.GetMembershipPort;
import com.jaypay.money.application.port.out.MembershipStatus;
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
        return new MembershipStatus(membershipId, membership != null && membership.valid());
    }

}
