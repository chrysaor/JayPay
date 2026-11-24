package com.jaypay.remittance.application.port.out.money;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoneyInfo {

    private String membershipId;
    private int balance;

}
