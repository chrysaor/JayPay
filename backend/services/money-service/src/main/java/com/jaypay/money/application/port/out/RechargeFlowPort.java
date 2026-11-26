package com.jaypay.money.application.port.out;

import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.RechargeFailure;

public interface RechargeFlowPort {

    void requested(MoneyChangingRequest request);

    void requestFailed(MoneyChangingRequest request, RechargeFailure failure);

    void walletIncreased(MoneyChangingRequest request);

    void failed(MoneyChangingRequest request, RechargeFailure failure);
}
