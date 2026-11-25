package com.jaypay.remittance.domain;

import java.util.Objects;

public record NewRemittance(RequestId requestId, String fromMembershipId, Receiver receiver, long amount) {

    public NewRemittance {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(fromMembershipId, "fromMembershipId");
        Objects.requireNonNull(receiver, "receiver");
        if (amount <= 0) {
            throw new IllegalArgumentException("A remittance amount is positive");
        }
        if (receiver instanceof Receiver.Member(String membershipId) && membershipId.equals(fromMembershipId)) {
            throw new IllegalArgumentException("A remittance goes to another member");
        }
    }
}
