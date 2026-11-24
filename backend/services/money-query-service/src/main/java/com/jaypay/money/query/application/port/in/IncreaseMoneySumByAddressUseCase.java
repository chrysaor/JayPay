package com.jaypay.money.query.application.port.in;

public interface IncreaseMoneySumByAddressUseCase {
    void increaseMoneySum(String membershipId, int moneyAmount);
}
