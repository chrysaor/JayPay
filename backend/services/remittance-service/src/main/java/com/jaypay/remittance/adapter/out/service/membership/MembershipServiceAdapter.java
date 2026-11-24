package com.jaypay.remittance.adapter.out.service.membership;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.remittance.application.port.out.membership.MembershipPort;
import com.jaypay.remittance.application.port.out.membership.MembershipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@ExternalSystemAdapter
@RequiredArgsConstructor
public class MembershipServiceAdapter implements MembershipPort {

    private final CommonHttpClient membershipServiceHttpClient;

    @Value("${service.membership.url}")
    private String membershipServiceEndpoint;

    @Override
    public MembershipStatus getMembershipStatus(String membershipId) {
        String buildUrl = String.join("/", this.membershipServiceEndpoint, "memberships", membershipId);
        MembershipResponse membership = membershipServiceHttpClient.get(buildUrl, MembershipResponse.class);
        return new MembershipStatus(membership.membershipId(), membership.valid());
    }
}
