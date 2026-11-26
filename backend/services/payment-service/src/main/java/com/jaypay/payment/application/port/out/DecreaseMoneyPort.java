package com.jaypay.payment.application.port.out;

public interface DecreaseMoneyPort {

    Debit decreaseMoney(String requestId, String membershipId, long amount);

    enum Debit {
        SUCCEEDED,
        FAILED,
        UNCONFIRMED
    }
}
