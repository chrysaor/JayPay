package com.jaypay.remittance.application.port.out.banking;

import lombok.Data;

@Data
public class BankingInfo {

    private String bankName;
    private String bankAccountNumber;
    private boolean isValid;

}
