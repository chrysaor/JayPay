package com.jaypay.settlement.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.settlement.application.port.in.SettlementUseCase;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payout;
import com.jaypay.settlement.application.port.out.PayoutAttempt;
import com.jaypay.settlement.application.port.out.PayoutAttemptPort;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import com.jaypay.settlement.application.port.out.SettlementFlowPort;
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
    private final SettlementFlowPort settlementFlowPort;
    private final PayoutAttemptPort payoutAttemptPort;

    @Override
    public void settleNormalStatusPayments() {
        Map<String, Optional<BankAccount>> accounts = new HashMap<>();
        for (Payment payment : paymentPort.getPaidPayments()) {
            Optional<BankAccount> account = accounts.computeIfAbsent(payment.franchiseId(), bankingPort::getRegisteredBankAccount);
            if (account.isEmpty()) {
                log.error("Merchant {} has no bank account, so payment {} stays paid", payment.franchiseId(), payment.paymentId());
                settlementFlowPort.waitingForAccount(payment);
            } else {
                settle(payment, account.get());
            }
        }
    }

    private void settle(Payment payment, BankAccount account) {
        settlementFlowPort.started(payment);
        PayoutAttempt attempt = payoutAttemptPort.current(payment.paymentId(), account);
        Payout payout = bankingPort.pay(attempt.requestId(), attempt.account(), payment.payout());
        if (payout == Payout.REFUSED && !attempt.account().equals(account)) {
            attempt = payoutAttemptPort.next(attempt, account);
            payout = bankingPort.pay(attempt.requestId(), attempt.account(), payment.payout());
        }
        settlementFlowPort.paidOut(payment, payout);
        switch (payout) {
            case PAID -> {
                if (paymentPort.finishSettlement(payment.paymentId())) {
                    settlementFlowPort.settled(payment);
                }
            }
            case REFUSED -> log.error("The bank refused the payout of payment {} to merchant {}, so it stays paid until the merchant registers another account",
                    payment.paymentId(), payment.franchiseId());
            case UNCONFIRMED -> log.warn("The bank did not confirm the payout of payment {}, so the next run asks again",
                    payment.paymentId());
        }
    }
}
