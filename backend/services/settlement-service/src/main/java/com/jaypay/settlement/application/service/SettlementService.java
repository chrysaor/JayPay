package com.jaypay.settlement.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.settlement.application.port.in.SettlementUseCase;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payout;
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
            } else {
                settle(payment, account.get());
            }
        }
    }

    private void settle(Payment payment, BankAccount account) {
        Payout payout = bankingPort.pay("settlement-" + payment.paymentId(), account, payment.payout());
        switch (payout) {
            case PAID -> paymentPort.finishSettlement(payment.paymentId());
            case REFUSED -> log.error("The bank refused the payout of payment {} to merchant {}, so it stays paid",
                    payment.paymentId(), payment.franchiseId());
            case UNCONFIRMED -> log.warn("The bank did not confirm the payout of payment {}, so the next run asks again",
                    payment.paymentId());
        }
    }
}
