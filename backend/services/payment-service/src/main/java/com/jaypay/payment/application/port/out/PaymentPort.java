package com.jaypay.payment.application.port.out;

import com.jaypay.payment.domain.NewPayment;
import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentPort {

    Payment claim(NewPayment payment);

    Payment conclude(long paymentId, PaymentStatus outcome);

    boolean settle(long paymentId);

    Optional<Payment> find(long paymentId);

    List<Payment> findPaid();
}
