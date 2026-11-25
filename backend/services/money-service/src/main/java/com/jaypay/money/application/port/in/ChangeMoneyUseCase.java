package com.jaypay.money.application.port.in;

import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.RequestId;

public interface ChangeMoneyUseCase {

    MoneyChangingRequest increaseMoney(ChangeMoneyCommand command);

    MoneyChangingRequest decreaseMoney(ChangeMoneyCommand command);

    MoneyChangingRequest increaseMoneyRequestAsync(ChangeMoneyCommand command);

    void increaseMoneyRequestByEvent(ChangeMoneyCommand command);

    boolean finishRecharging(RequestId requestId);

}
