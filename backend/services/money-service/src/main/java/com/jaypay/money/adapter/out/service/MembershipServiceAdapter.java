package com.jaypay.money.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.HttpReply;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.money.application.port.out.GetMembershipPort;
import com.jaypay.money.application.port.out.MembershipStatus;
import com.jaypay.money.domain.MembershipNotConfirmedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.net.HttpURLConnection;


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
        HttpReply<MembershipResponse> reply;
        try {
            reply = commonHttpClient.get(url, MembershipResponse.class);
        } catch (UncheckedIOException unanswered) {
            throw new MembershipNotConfirmedException(membershipId, unanswered.getMessage());
        }
        if (reply.status() == HttpURLConnection.HTTP_NOT_FOUND) {
            return new MembershipStatus(membershipId, false);
        }
        if (reply.body() == null) {
            throw new MembershipNotConfirmedException(membershipId, "status " + reply.status());
        }
        return new MembershipStatus(membershipId, reply.body().valid());
    }

}
