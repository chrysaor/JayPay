package com.jaypay.payment.application.port.in;

import com.jaypay.payment.domain.RequestId;

import java.util.Objects;

public record RequestPaymentCommand(RequestId requestId, String requestMembershipId, String franchiseId, long requestPrice) {

    public RequestPaymentCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestMembershipId, "requestMembershipId");
        Objects.requireNonNull(franchiseId, "franchiseId");
    }
}
