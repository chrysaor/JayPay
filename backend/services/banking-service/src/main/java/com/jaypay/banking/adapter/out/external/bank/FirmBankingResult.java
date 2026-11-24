package com.jaypay.banking.adapter.out.external.bank;

import lombok.Data;

@Data
public class FirmBankingResult {

    private int resultCode; // 0: Success, 1: Failure

    public FirmBankingResult(int resultCode) {
        this.resultCode = resultCode;
    }
}
