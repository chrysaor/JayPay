package com.jaypay.remittance.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.ExternalSystemAdapter;
import com.jaypay.common.HttpReply;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.remittance.application.port.out.MembershipPort;
import com.jaypay.remittance.domain.MembershipNotConfirmedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.UncheckedIOException;
import java.net.HttpURLConnection;

@ExternalSystemAdapter
@Slf4j
public class MembershipServiceAdapter implements MembershipPort {

    private final CommonHttpClient commonHttpClient;
    private final String membershipServiceUrl;

    public MembershipServiceAdapter(CommonHttpClient commonHttpClient, @Value("${service.membership.url}") String membershipServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.membershipServiceUrl = membershipServiceUrl;
    }

    @Override
    public boolean isValid(String membershipId) {
        String url = String.join("/", membershipServiceUrl, "memberships", membershipId);
        HttpReply<MembershipResponse> reply;
        try {
            reply = commonHttpClient.get(url, MembershipResponse.class);
        } catch (UncheckedIOException unanswered) {
            log.warn("membership-service did not answer about member {}", membershipId, unanswered);
            throw new MembershipNotConfirmedException(membershipId, unanswered.getMessage());
        }
        if (reply.status() != HttpURLConnection.HTTP_NOT_FOUND && reply.body() == null) {
            log.warn("membership-service did not confirm member {}: status {}", membershipId, reply.status());
            throw new MembershipNotConfirmedException(membershipId, "status " + reply.status());
        }
        MembershipResponse membership = reply.body();
        return membership != null && membershipId.equals(membership.membershipId()) && membership.valid();
    }
}
