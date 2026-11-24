package com.jaypay.settlement.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.settlement.application.port.in.SettlementUseCase;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UseCase
@RequiredArgsConstructor
public class SettlementService implements SettlementUseCase {

    private final BankingPort bankingPort;
    private final PaymentPort paymentPort;

    @Override
    public void settleNormalStatusPayments() {
        List<Payment> payments = paymentPort.getNormalStatusPayments();

        Map<String, BankAccount> accountByFranchise = new LinkedHashMap<>();
        Map<String, Integer> amountByFranchise = new LinkedHashMap<>();
        for (Payment payment : payments) {
            accountByFranchise.computeIfAbsent(payment.franchiseId(), bankingPort::getRegisteredBankAccount);
            amountByFranchise.merge(payment.franchiseId(), settledAmount(payment), Integer::sum);
        }

        accountByFranchise.forEach((franchiseId, account) ->
                bankingPort.requestFirmBanking(account, amountByFranchise.get(franchiseId)));

        for (Payment payment : payments) {
            paymentPort.finishSettlement(payment.paymentId());
        }
    }

    private static int settledAmount(Payment payment) {
        double fee = Double.parseDouble(payment.franchiseFeeRate());
        return (int) ((100 - fee) * payment.requestPrice() * 100);
    }
}
