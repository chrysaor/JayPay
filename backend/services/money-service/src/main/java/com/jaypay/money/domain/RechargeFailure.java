package com.jaypay.money.domain;

public enum RechargeFailure {
    NO_BANK_ACCOUNT("The member has no registered bank account"),
    MEMBER_NOT_CONFIRMED("membership-service did not confirm the member"),
    BANK_ACCOUNT_NOT_CONFIRMED("The bank did not confirm the account"),
    CHARGE_REFUSED("The bank refused the charge"),
    DEADLINE_PASSED("The recharge did not finish before its deadline");

    private final String reason;

    RechargeFailure(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
