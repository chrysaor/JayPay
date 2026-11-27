package com.jaypay.money.application.port.in;

import com.jaypay.money.domain.RequestId;

import java.util.Objects;

public record RefundMoneyCommand(RequestId requestId, RequestId refundedRequestId) {

    public RefundMoneyCommand {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(refundedRequestId, "refundedRequestId");
    }
}
