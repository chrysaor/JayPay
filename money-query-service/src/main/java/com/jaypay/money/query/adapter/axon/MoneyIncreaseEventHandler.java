package com.jaypay.money.query.adapter.axon;

import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.money.query.application.port.out.GetMemberAddressInfoPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import com.jaypay.money.query.application.port.out.MemberAddressInfo;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
public class MoneyIncreaseEventHandler {
    @EventHandler
    public void handler(RequestFirmBankingFinishedEvent event, GetMemberAddressInfoPort getMemberAddressInfoPort, InsertMoneyIncreaseEventByAddress insertMoneyIncreaseEventByAddress) {
        System.out.println("Money Increase Event Received: "+ event.toString());

        // Address information of the customer
        MemberAddressInfo memberAddressInfo = getMemberAddressInfoPort.getMemberAddressInfo(event.getMembershipId());

        // Insert DynamoDB
        String address = memberAddressInfo.getAddress();
        int moneyIncrease = event.getMoneyAmount();

        insertMoneyIncreaseEventByAddress.insertMoneyIncreaseEventByAddress(
                address, moneyIncrease
        );
    }
}
