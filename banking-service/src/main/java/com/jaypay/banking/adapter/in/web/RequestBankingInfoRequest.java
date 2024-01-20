package com.jaypay.banking.adapter.in.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestBankingInfoRequest {
    // a -> b 실물계좌 validation 확인용
    private String bankName;
    private String bankAccountNumber;
}