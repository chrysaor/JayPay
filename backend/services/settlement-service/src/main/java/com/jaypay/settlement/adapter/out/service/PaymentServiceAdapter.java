package com.jaypay.settlement.adapter.out.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jaypay.common.CommonHttpClient;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentServiceAdapter implements PaymentPort {

    private final CommonHttpClient commonHttpClient;
    private final String paymentServiceUrl;

    public PaymentServiceAdapter(CommonHttpClient commonHttpClient,
                                 @Value("${service.payment.url}") String paymentServiceUrl) {
        this.commonHttpClient = commonHttpClient;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    @Override
    public List<Payment> getPaidPayments() {
        String url = String.join("/", paymentServiceUrl, "payment/normal-status");
        List<Payment> payments = commonHttpClient.get(url, new TypeReference<>() {});
        return payments == null ? List.of() : payments;
    }

    @Override
    public boolean finishSettlement(long paymentId) {
        String url = String.join("/", paymentServiceUrl, "payment/finish-settlement");
        SettlementResult result = commonHttpClient.post(url, new FinishSettlementRequest(String.valueOf(paymentId)), SettlementResult.class);
        return result != null
                && result.paymentId() == paymentId
                && "SETTLED".equals(result.paymentStatus())
                && Boolean.FALSE.equals(result.alreadySettled());
    }

    record FinishSettlementRequest(String paymentId) {
    }

    record SettlementResult(long paymentId, String paymentStatus, Boolean alreadySettled) {
    }
}
