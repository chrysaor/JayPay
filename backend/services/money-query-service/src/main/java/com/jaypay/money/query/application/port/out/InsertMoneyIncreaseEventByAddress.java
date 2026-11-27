package com.jaypay.money.query.application.port.out;

public interface InsertMoneyIncreaseEventByAddress {
    void insertMoneyIncreaseEventByAddress(String rechargingRequestId, String addressName, long moneyIncrease);
}
