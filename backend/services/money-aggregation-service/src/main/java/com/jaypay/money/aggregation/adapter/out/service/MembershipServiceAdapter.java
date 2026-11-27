package com.jaypay.money.aggregation.adapter.out.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.HttpReply;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.money.aggregation.application.port.out.GetMembershipPort;
import com.jaypay.money.aggregation.domain.MoneySumNotConfirmedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.UncheckedIOException;
import java.util.List;


@ExternalSystemAdapter
@Slf4j
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
        HttpReply<List<MembershipResponse>> reply;
        try {
            reply = commonHttpClient.get(url, new TypeReference<>() {});
        } catch (UncheckedIOException unanswered) {
            log.warn("membership-service did not answer the members at an address", unanswered);
            throw new MoneySumNotConfirmedException("membership-service", unanswered.getMessage());
        }
        if (reply.body() == null) {
            log.warn("membership-service did not answer the members at an address: status {}", reply.status());
            throw new MoneySumNotConfirmedException("membership-service", "status " + reply.status());
        }
        return reply.body().stream()
                .map(MembershipResponse::membershipId)
                .toList();
    }
}
