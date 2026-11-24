package com.jaypay.settlement.port.out;

import com.jaypay.settlement.adapter.out.service.Payment;

import java.util.List;

public interface PaymentPort {

    List<Payment> getNormalStatusPayments();
    void finishSettlement(Long paymentId);

}
