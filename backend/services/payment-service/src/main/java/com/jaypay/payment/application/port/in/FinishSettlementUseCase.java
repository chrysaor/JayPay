package com.jaypay.payment.application.port.in;

import com.jaypay.payment.domain.Payment;

import java.util.List;

public interface FinishSettlementUseCase {

    List<Payment> getNormalStatusPayments();
    void finishPayment(FinishSettlementCommand command);

}
