package com.jaypay.money.adapter.in.web;

import com.jaypay.money.application.port.in.ChangeMoneyCommand;
import com.jaypay.money.application.port.in.RefundMoneyCommand;
import com.jaypay.money.domain.MoneyAmount;
import com.jaypay.money.domain.RequestId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
class MoneyChangingCommands {

    private final BigDecimal maxAmount;

    MoneyChangingCommands(@Value("${money.amount.max}") long maxAmount) {
        this.maxAmount = BigDecimal.valueOf(maxAmount);
    }

    ChangeMoneyCommand parse(MoneyChangingRequestBody body) {
        return new ChangeMoneyCommand(requestId(body.requestId()), membershipId(body.targetMembershipId()), amount(body.amount()));
    }

    RefundMoneyCommand parse(RefundRequestBody body) {
        return new RefundMoneyCommand(requestId(body.requestId()), requestId(body.refundedRequestId()));
    }

    private static RequestId requestId(String value) {
        try {
            return new RequestId(value);
        } catch (IllegalArgumentException e) {
            throw new MoneyRequestRejected("INVALID_REQUEST_ID", "Send a requestId of 1 to 64 letters, digits, '-' or '_'.");
        }
    }

    private static String membershipId(String value) {
        if (value == null || !value.matches("[0-9]{1,18}")) {
            throw new MoneyRequestRejected("INVALID_MEMBERSHIP_ID", "Send the targetMembershipId as a member number.");
        }
        return value;
    }

    private MoneyAmount amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(maxAmount) > 0) {
            throw new MoneyRequestRejected("INVALID_AMOUNT", "Send an amount that is a whole number from 1 to " + maxAmount.toPlainString() + ".");
        }
        return new MoneyAmount(value.longValueExact());
    }
}
