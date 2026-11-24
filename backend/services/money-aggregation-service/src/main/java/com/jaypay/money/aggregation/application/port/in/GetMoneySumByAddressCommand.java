package com.jaypay.money.aggregation.application.port.in;

import java.util.Objects;

public record GetMoneySumByAddressCommand(String address) {
    public GetMoneySumByAddressCommand {
        Objects.requireNonNull(address, "address");
    }
}
