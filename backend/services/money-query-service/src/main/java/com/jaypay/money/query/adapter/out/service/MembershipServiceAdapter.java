package com.jaypay.money.query.adapter.out.service;

import com.jaypay.common.CommonHttpClient;
import com.jaypay.common.HttpReply;
import com.jaypay.common.membership.MembershipResponse;
import com.jaypay.money.query.application.port.out.GetMemberAddressInfoPort;
import com.jaypay.money.query.application.port.out.MemberAddressInfo;
import com.jaypay.money.query.domain.MemberAddressNotConfirmedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.util.Optional;

@Component
public class MembershipServiceAdapter implements GetMemberAddressInfoPort {

    private final CommonHttpClient commonHttpClient;
    private final String membershipServiceUrl;

    public MembershipServiceAdapter(CommonHttpClient commonHttpClient,
                                    @Value("${service.membership.url}") String membershipServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.membershipServiceUrl = membershipServiceUrl;
    }

    @Override
    public Optional<MemberAddressInfo> getMemberAddressInfo(String membershipId) {
        String url = String.join("/", membershipServiceUrl, "memberships", membershipId);
        HttpReply<MembershipResponse> reply;
        try {
            reply = commonHttpClient.get(url, MembershipResponse.class);
        } catch (UncheckedIOException unanswered) {
            throw new MemberAddressNotConfirmedException(membershipId, unanswered.getMessage());
        }
        if (reply.status() == HttpURLConnection.HTTP_NOT_FOUND) {
            return Optional.empty();
        }
        MembershipResponse membership = reply.body();
        if (membership == null) {
            throw new MemberAddressNotConfirmedException(membershipId, "status " + reply.status());
        }
        return Optional.of(new MemberAddressInfo(membership.membershipId(), membership.address()));
    }
}
