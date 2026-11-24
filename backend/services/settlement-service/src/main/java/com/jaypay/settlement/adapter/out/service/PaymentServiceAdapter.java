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
    public List<Payment> getNormalStatusPayments() {
        String url = String.join("/", paymentServiceUrl, "payment/normal-status");
        return commonHttpClient.get(url, new TypeReference<>() {});
    }

    @Override
    public void finishSettlement(long paymentId) {
        String url = String.join("/", paymentServiceUrl, "payment/finish-settlement");
        commonHttpClient.post(url, new FinishSettlementRequest(String.valueOf(paymentId)));
    }

    record FinishSettlementRequest(String paymentId) {
    }
}
