package com.jaypay.payment.adapter.in.schedule;

import com.jaypay.payment.application.port.in.ResumeUnconfirmedPaymentsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentSweeper {

    private final ResumeUnconfirmedPaymentsUseCase resumeUnconfirmedPaymentsUseCase;

    @Scheduled(fixedDelayString = "${payment.sweep.every}")
    void sweep() {
        resumeUnconfirmedPaymentsUseCase.resumeUnconfirmedPayments();
    }
}
