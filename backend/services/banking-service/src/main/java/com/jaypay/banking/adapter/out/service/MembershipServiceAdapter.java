package com.jaypay.banking.adapter.out.service;

import com.jaypay.banking.application.port.out.GetMembershipPort;
import com.jaypay.banking.application.port.out.MembershipStatus;
import com.jaypay.banking.domain.MembershipNotConfirmedException;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.HttpReply;
import com.jaypay.common.membership.MembershipResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.net.HttpURLConnection;

@Component
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
    public MembershipStatus getMembership(String membershipId) {
        String url = String.join("/", membershipServiceUrl, "memberships", membershipId);
        HttpReply<MembershipResponse> reply;
        try {
            reply = commonHttpClient.get(url, MembershipResponse.class);
        } catch (UncheckedIOException unanswered) {
            log.warn("membership-service did not answer about member {}", membershipId, unanswered);
            throw new MembershipNotConfirmedException(membershipId, unanswered.getMessage());
        }
        if (reply.status() == HttpURLConnection.HTTP_NOT_FOUND) {
            return new MembershipStatus(membershipId, false);
        }
        if (reply.body() == null) {
            log.warn("membership-service did not confirm member {}: status {}", membershipId, reply.status());
            throw new MembershipNotConfirmedException(membershipId, "status " + reply.status());
        }
        return new MembershipStatus(membershipId, reply.body().valid());
    }

}
