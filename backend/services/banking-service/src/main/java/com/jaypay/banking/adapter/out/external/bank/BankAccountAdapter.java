package com.jaypay.banking.adapter.out.external.bank;

import com.jaypay.banking.application.port.out.BankAnswer;
import com.jaypay.banking.application.port.out.ExternalBankPort;
import com.jaypay.banking.application.port.out.RequestBankAccountInfoPort;
import com.jaypay.banking.domain.BankTransfer;
import com.jaypay.banking.domain.ExternalBankingInfo;
import com.jaypay.common.ExternalSystemAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ExternalSystemAdapter
public class BankAccountAdapter implements RequestBankAccountInfoPort, ExternalBankPort {

    static final String REFUSED_ACCOUNT_SUFFIX = "-refused";

    private final Map<String, BankAnswer> answered = new ConcurrentHashMap<>();

    @Override
    public ExternalBankingInfo getBankAccountInfo(String bankName, String bankAccountNumber) {
        return ExternalBankingInfo.generatedExternalBankingInfo(
                new ExternalBankingInfo.BankName(bankName),
                new ExternalBankingInfo.BankAccountNumber(bankAccountNumber),
                new ExternalBankingInfo.IsValid(true)
        );
    }

    @Override
    public BankAnswer send(BankTransfer transfer) {
        return answered.computeIfAbsent(transfer.requestId(), requestId ->
                refuses(transfer.fromBankAccountNumber()) || refuses(transfer.toBankAccountNumber()) ? BankAnswer.REFUSED : BankAnswer.ACCEPTED);
    }

    private static boolean refuses(String bankAccountNumber) {
        return bankAccountNumber.endsWith(REFUSED_ACCOUNT_SUFFIX);
    }
}
