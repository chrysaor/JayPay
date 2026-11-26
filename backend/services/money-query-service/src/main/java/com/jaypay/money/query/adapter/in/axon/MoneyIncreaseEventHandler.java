package com.jaypay.money.query.adapter.in.axon;

import com.jaypay.common.event.WalletRechargedEvent;
import com.jaypay.money.query.application.port.in.IncreaseMoneySumByAddressUseCase;
import lombok.RequiredArgsConstructor;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ProcessingGroup("com.jaypay.money.query.adapter.axon")
public class MoneyIncreaseEventHandler {

    private final IncreaseMoneySumByAddressUseCase increaseMoneySumByAddressUseCase;

    @EventHandler
    public void handler(WalletRechargedEvent event) {
        increaseMoneySumByAddressUseCase.increaseMoneySum(event.rechargingRequestId(), event.membershipId(), event.amount());
    }
}
