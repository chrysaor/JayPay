package com.jaypay.payment.application.port.in;

import com.jaypay.payment.domain.Payment;

public interface RequestPaymentUseCase {
    Payment requestPayment(RequestPaymentCommand command);
}
