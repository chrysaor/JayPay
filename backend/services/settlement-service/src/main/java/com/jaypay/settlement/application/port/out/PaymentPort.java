package com.jaypay.settlement.application.port.out;

import java.util.List;

public interface PaymentPort {

    List<Payment> getPaidPayments();

    boolean finishSettlement(long paymentId);
}
