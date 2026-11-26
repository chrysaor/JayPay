package com.jaypay.settlement.application.port.out;

public interface PayoutAttemptPort {

    PayoutAttempt current(long paymentId, BankAccount account);

    PayoutAttempt next(PayoutAttempt refused, BankAccount account);
}
