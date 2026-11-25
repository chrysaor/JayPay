package com.jaypay.money.aggregation.application.port.in;

public interface GetMoneySumByAddressUseCase {
    long getMoneySumByAddress(GetMoneySumByAddressCommand command);
}
