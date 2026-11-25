package com.jaypay.remittance.application.port.out.money;

public interface MoneyPort {

    MoneyInfo getMoneyInfo(String membershipId);
    boolean requestMoneyRecharging(String requestId, String membershipId, long amount);
    boolean requestMoneyIncrease(String requestId, String membershipId, long amount);
    boolean requestMoneyDecrease(String requestId, String membershipId, long amount);

}
