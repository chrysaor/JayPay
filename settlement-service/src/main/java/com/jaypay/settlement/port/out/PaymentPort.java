package com.jaypay.settlement.port.out;

import com.jaypay.settlement.adapter.out.service.Payment;

import java.util.List;

public interface PaymentPort {

    // membershipId = franchiseId -> temporary
    List<Payment> getNormalStatusPayments();
    // Target account, amount
    void finishSettlement(Long paymentId);

}
