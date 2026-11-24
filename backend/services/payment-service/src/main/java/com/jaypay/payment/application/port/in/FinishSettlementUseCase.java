package com.jaypay.payment.application.port.in;

import com.jaypay.payment.domain.Payment;

import java.util.List;

public interface FinishSettlementUseCase {

    // It should be comprised with command, start date and end date
    List<Payment> getNormalStatusPayments();
    void finishPayment(FinishSettlementCommand command);

}
