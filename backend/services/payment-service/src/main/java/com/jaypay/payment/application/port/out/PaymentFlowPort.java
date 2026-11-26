package com.jaypay.payment.application.port.out;

import com.jaypay.payment.domain.NewPayment;
import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentRejectedException.Reason;

public interface PaymentFlowPort {

    void refused(NewPayment payment, Reason reason);

    void requested(Payment payment);

    void concluded(Payment payment);
}
