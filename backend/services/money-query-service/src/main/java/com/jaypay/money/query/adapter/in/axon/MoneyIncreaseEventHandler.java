package com.jaypay.money.query.adapter.in.axon;

import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.money.query.application.port.in.IncreaseMoneySumByAddressUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ProcessingGroup("com.jaypay.money.query.adapter.axon")
public class MoneyIncreaseEventHandler {

    private final IncreaseMoneySumByAddressUseCase increaseMoneySumByAddressUseCase;

    @EventHandler
    public void handler(RequestFirmBankingFinishedEvent event) {
        if (!event.succeeded()) {
            log.info("Recharge {} did not land at the bank, so the region total leaves it out", event.getRechargingRequestId());
            return;
        }
        increaseMoneySumByAddressUseCase.increaseMoneySum(event.getMembershipId(), Math.toIntExact(event.getMoneyAmount()));
    }
}
