package com.jaypay.money.application.port.in;

import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.RechargeFailure;
import com.jaypay.money.domain.RequestId;

public interface ChangeMoneyUseCase {

    MoneyChangingRequest increaseMoney(ChangeMoneyCommand command);

    MoneyChangingRequest decreaseMoney(ChangeMoneyCommand command);

    MoneyChangingRequest refundMoney(RefundMoneyCommand command);

    MoneyChangingRequest increaseMoneyRequestByEvent(ChangeMoneyCommand command);

    boolean finishRecharging(RequestId requestId);

    boolean failRecharging(RequestId requestId, RechargeFailure failure);

    void leaveRollbackUnconfirmed(RequestId requestId);

}
