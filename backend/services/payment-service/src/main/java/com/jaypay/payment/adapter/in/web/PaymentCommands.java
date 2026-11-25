package com.jaypay.payment.adapter.in.web;

import com.jaypay.payment.application.port.in.RequestPaymentCommand;
import com.jaypay.payment.domain.RequestId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
class PaymentCommands {

    private static final String MEMBER_NUMBER = "[0-9]{1,18}";

    private final BigDecimal maxAmount;

    PaymentCommands(@Value("${payment.amount.max}") long maxAmount) {
        this.maxAmount = BigDecimal.valueOf(maxAmount);
    }

    RequestPaymentCommand parse(PaymentRequest body) {
        return new RequestPaymentCommand(
                requestId(body.requestId()),
                memberNumber(body.requestMembershipId(), "INVALID_PAYER", "Send the requestMembershipId as a member number."),
                memberNumber(body.franchiseId(), "INVALID_MERCHANT", "Send the franchiseId as the member number of a corporate member."),
                amount(body.requestPrice()));
    }

    long paymentId(FinishSettlementRequest body) {
        if (body.paymentId() == null || !body.paymentId().matches(MEMBER_NUMBER)) {
            throw new PaymentRequestRejected("INVALID_PAYMENT_ID", "Send the paymentId as a number.");
        }
        return Long.parseLong(body.paymentId());
    }

    private static RequestId requestId(String value) {
        try {
            return new RequestId(value);
        } catch (IllegalArgumentException e) {
            throw new PaymentRequestRejected("INVALID_REQUEST_ID", "Send a requestId of 1 to 64 letters, digits, '-' or '_'.");
        }
    }

    private static String memberNumber(String value, String error, String message) {
        if (value == null || !value.matches(MEMBER_NUMBER)) {
            throw new PaymentRequestRejected(error, message);
        }
        return value;
    }

    private long amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(maxAmount) > 0) {
            throw new PaymentRequestRejected("INVALID_AMOUNT", "Send a requestPrice that is a whole number from 1 to " + maxAmount.toPlainString() + ".");
        }
        return value.longValueExact();
    }
}
