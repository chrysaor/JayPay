package com.jaypay.money.aggregation.adapter.out.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.money.aggregation.application.port.out.GetMembershipPort;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;


@ExternalSystemAdapter
public class MembershipServiceAdapter implements GetMembershipPort {

    private final CommonHttpClient commonHttpClient;

    private final String membershipServiceUrl;

    public MembershipServiceAdapter(CommonHttpClient commonHttpClient,
                                    @Value("${service.membership.url}") String membershipServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.membershipServiceUrl = membershipServiceUrl;
    }

    @Override
    public List<String> getMembershipByAddress(String address) {
        String url = String.join("/", membershipServiceUrl, "memberships/address", address);
        List<MembershipResponse> memberships = commonHttpClient.get(url, new TypeReference<>() {});
        return memberships.stream()
                .map(MembershipResponse::membershipId)
                .toList();
    }
}
