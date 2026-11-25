package com.jaypay.money.adapter.in.web;

public class MoneyController {

    public String increase() {
        return "increase";
    }

    public String transfer(String bankName, String bankAccountNumber, long amount) {
        return bankName + bankAccountNumber + amount;
    }
}
