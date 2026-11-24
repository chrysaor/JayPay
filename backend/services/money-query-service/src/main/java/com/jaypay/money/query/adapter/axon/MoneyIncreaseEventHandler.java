package com.jaypay.money.query.adapter.axon;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.money.query.application.port.out.GetMemberAddressInfoPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import com.jaypay.money.query.application.port.out.MemberAddressInfo;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MoneyIncreaseEventHandler {
    @EventHandler
    public void handler(RequestFirmBankingFinishedEvent event, GetMemberAddressInfoPort getMemberAddressInfoPort, InsertMoneyIncreaseEventByAddress insertMoneyIncreaseEventByAddress) {
        log.info("Money Increase Event Received: {}", event);

        MemberAddressInfo memberAddressInfo = getMemberAddressInfoPort.getMemberAddressInfo(event.getMembershipId());

        String address = memberAddressInfo.getAddress();
        int moneyIncrease = event.getMoneyAmount();

        insertMoneyIncreaseEventByAddress.insertMoneyIncreaseEventByAddress(
                address, moneyIncrease
        );
    }
}
