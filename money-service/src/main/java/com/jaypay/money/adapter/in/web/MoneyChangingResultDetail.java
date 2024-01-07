package com.jaypay.money.adapter.in.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoneyChangingResultDetail {
    private String moneyChangingRequestId;
    private int moneyChangingType;
    private int moneyChangingResultStatus; // enum, 0: 중액, 1: 감액
    private int amount;
}
