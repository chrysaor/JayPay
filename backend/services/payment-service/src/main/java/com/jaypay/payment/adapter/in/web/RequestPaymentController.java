package com.jaypay.payment.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.payment.application.port.in.FinishSettlementUseCase;
import com.jaypay.payment.application.port.in.RequestPaymentUseCase;
import com.jaypay.payment.application.port.in.SettlementOutcome;
import com.jaypay.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class RequestPaymentController {

    private final PaymentCommands paymentCommands;
    private final RequestPaymentUseCase requestPaymentUseCase;
    private final FinishSettlementUseCase finishSettlementUseCase;

    @PostMapping(path = "/payment/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    PaymentDetail requestPayment(@RequestBody PaymentRequest request) {
        return PaymentDetail.of(requestPaymentUseCase.requestPayment(paymentCommands.parse(request)));
    }

    @GetMapping(path = "/payment/normal-status")
    List<PaymentDetail> getPaidPayments() {
        return finishSettlementUseCase.getPaidPayments().stream().map(PaymentDetail::of).toList();
    }

    @PostMapping(path = "/payment/finish-settlement", consumes = MediaType.APPLICATION_JSON_VALUE)
    SettlementResult finishSettlement(@RequestBody FinishSettlementRequest request) {
        SettlementOutcome outcome = finishSettlementUseCase.finishSettlement(paymentCommands.paymentId(request));
        return new SettlementResult(outcome.payment().paymentId(), outcome.payment().paymentStatus(), outcome.alreadySettled());
    }

    record SettlementResult(long paymentId, PaymentStatus paymentStatus, boolean alreadySettled) {
    }
}
