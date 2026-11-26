package com.jaypay.money.query.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.query.application.port.in.IncreaseMoneySumByAddressUseCase;
import com.jaypay.money.query.application.port.out.GetMemberAddressInfoPort;
import com.jaypay.money.query.application.port.out.InsertMoneyIncreaseEventByAddress;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class IncreaseMoneySumByAddressService implements IncreaseMoneySumByAddressUseCase {

    private final GetMemberAddressInfoPort getMemberAddressInfoPort;
    private final InsertMoneyIncreaseEventByAddress insertMoneyIncreaseEventByAddress;

    @Override
    public void increaseMoneySum(String rechargingRequestId, String membershipId, long moneyAmount) {
        String address = getMemberAddressInfoPort.getMemberAddressInfo(membershipId).getAddress();
        insertMoneyIncreaseEventByAddress.insertMoneyIncreaseEventByAddress(rechargingRequestId, address, moneyAmount);
    }
}
