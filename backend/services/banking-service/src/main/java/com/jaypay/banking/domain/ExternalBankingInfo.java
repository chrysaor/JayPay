package com.jaypay.banking.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExternalBankingInfo {

    private final String bankName;
    private final String bankAccountNumber;
    private final boolean isValid;

    public static ExternalBankingInfo generatedExternalBankingInfo(
            BankName bankName,
            BankAccountNumber bankAccountNumber,
            IsValid isValid
    ) {
        return new ExternalBankingInfo(
                bankName.bankName,
                bankAccountNumber.bankAccountNumber,
                isValid.isValid
        );
    }

    @Value
    public static class BankName {
        public BankName(String value) {
            this.bankName = value;
        }

        String bankName;
    }

    @Value
    public static class BankAccountNumber {
        public BankAccountNumber(String value) {
            this.bankAccountNumber = value;
        }

        String bankAccountNumber;
    }

    @Value
    public static class IsValid {
        public IsValid(boolean value) {
            this.isValid = value;
        }

        boolean isValid;
    }

}