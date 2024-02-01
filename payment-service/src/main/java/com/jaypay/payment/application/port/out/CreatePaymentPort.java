package com.jaypay.payment.application.port.out;

import com.jaypay.payment.domain.Payment;

public interface CreatePaymentPort {
    Payment createPayment(String requestMembershipId, String requestPrice, String franchiseId, String franchiseFeeRate);
}
