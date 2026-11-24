package com.jaypay.payment.application.port.out;

import com.jaypay.payment.domain.Payment;

import java.util.List;

public interface FinishSettlementPort {
    List<Payment> getNormalStatusPayments();
    void changePaymentRequestStatus(String paymentId, int status);
}
