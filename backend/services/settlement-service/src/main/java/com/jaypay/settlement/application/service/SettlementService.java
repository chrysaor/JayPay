package com.jaypay.settlement.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.settlement.application.port.in.SettlementUseCase;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
@Slf4j
public class SettlementService implements SettlementUseCase {

    private final BankingPort bankingPort;
    private final PaymentPort paymentPort;

    @Override
    public void settleNormalStatusPayments() {
        Map<String, Optional<BankAccount>> accounts = new HashMap<>();
        for (Payment payment : paymentPort.getPaidPayments()) {
            Optional<BankAccount> account = accounts.computeIfAbsent(payment.franchiseId(), bankingPort::getRegisteredBankAccount);
            if (account.isEmpty()) {
                log.warn("Merchant {} has no bank account, so payment {} stays paid", payment.franchiseId(), payment.paymentId());
            } else if (paymentPort.finishSettlement(payment.paymentId())) {
                pay(payment, account.get());
            }
        }
    }

    private void pay(Payment payment, BankAccount account) {
        try {
            if (!bankingPort.requestFirmBanking(account, payment.payout())) {
                log.error("The bank refused the payout of payment {} to merchant {}", payment.paymentId(), payment.franchiseId());
            }
        } catch (RuntimeException e) {
            log.error("The payout of payment {} to merchant {} got no answer", payment.paymentId(), payment.franchiseId(), e);
        }
    }
}
