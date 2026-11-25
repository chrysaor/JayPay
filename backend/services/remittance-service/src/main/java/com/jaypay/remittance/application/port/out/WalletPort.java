package com.jaypay.remittance.application.port.out;

public interface WalletPort {

    Outcome decrease(String requestId, String membershipId, long amount);

    Outcome increase(String requestId, String membershipId, long amount);
}
