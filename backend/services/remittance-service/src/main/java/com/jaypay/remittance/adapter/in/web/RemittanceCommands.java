package com.jaypay.remittance.adapter.in.web;

import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Receiver;
import com.jaypay.remittance.domain.RequestId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
class RemittanceCommands {

    private static final String MEMBER_NUMBER = "[0-9]{1,18}";
    private static final int TO_MEMBER = 0;
    private static final int TO_BANK_ACCOUNT = 1;
    private static final int BANK_FIELD_MAX = 64;

    private final BigDecimal maxAmount;

    RemittanceCommands(@Value("${remittance.amount.max}") long maxAmount) {
        this.maxAmount = BigDecimal.valueOf(maxAmount);
    }

    NewRemittance parse(RemittanceRequest body) {
        RequestId requestId = requestId(body.requestId());
        String sender = memberNumber(body.fromMembershipId(), "INVALID_SENDER", "Send the fromMembershipId as a member number.");
        Receiver receiver = receiver(body, sender);
        return new NewRemittance(requestId, sender, receiver, amount(body.amount()));
    }

    String membershipId(String value) {
        return memberNumber(value, "INVALID_MEMBERSHIP_ID", "Send the membershipId as a member number.");
    }

    private static RequestId requestId(String value) {
        try {
            return new RequestId(value);
        } catch (IllegalArgumentException e) {
            throw new RemittanceRequestRejected("INVALID_REQUEST_ID", "Send a requestId of 1 to 64 letters, digits, '-' or '_'.");
        }
    }

    private static Receiver receiver(RemittanceRequest body, String sender) {
        if (body.remittanceType() == null || (body.remittanceType() != TO_MEMBER && body.remittanceType() != TO_BANK_ACCOUNT)) {
            throw new RemittanceRequestRejected("INVALID_REMITTANCE_TYPE",
                    "Send a remittanceType of 0 for a member or 1 for a bank account.");
        }
        if (body.remittanceType() == TO_MEMBER) {
            String receiver = memberNumber(body.toMembershipId(), "INVALID_RECEIVER", "Send the toMembershipId as a member number.");
            if (receiver.equals(sender)) {
                throw new RemittanceRequestRejected("INVALID_RECEIVER", "Send the money to a member other than the sender.");
            }
            return new Receiver.Member(receiver);
        }
        if (!bankField(body.toBankName()) || !bankField(body.toBankAccountNumber())) {
            throw new RemittanceRequestRejected("INVALID_BANK_ACCOUNT",
                    "Send a toBankName and a toBankAccountNumber of 1 to " + BANK_FIELD_MAX + " characters.");
        }
        return new Receiver.BankAccount(body.toBankName(), body.toBankAccountNumber());
    }

    private static boolean bankField(String value) {
        return value != null && !value.isBlank() && value.length() <= BANK_FIELD_MAX;
    }

    private static String memberNumber(String value, String error, String message) {
        if (value == null || !value.matches(MEMBER_NUMBER)) {
            throw new RemittanceRequestRejected(error, message);
        }
        return value;
    }

    private long amount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(maxAmount) > 0) {
            throw new RemittanceRequestRejected("INVALID_AMOUNT", "Send an amount that is a whole number from 1 to " + maxAmount.toPlainString() + ".");
        }
        return value.longValueExact();
    }
}
