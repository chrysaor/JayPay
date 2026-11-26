package com.jaypay.settlement.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.PayoutAttempt;
import com.jaypay.settlement.application.port.out.PayoutAttemptPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapter
@RequiredArgsConstructor
public class PayoutAttemptPersistenceAdapter implements PayoutAttemptPort {

    private final SpringDataPayoutAttemptRepository repository;

    @Override
    @Transactional
    public PayoutAttempt current(long paymentId, BankAccount account) {
        repository.claim(paymentId, account.bankName(), account.bankAccountNumber());
        return stored(paymentId);
    }

    @Override
    @Transactional
    public PayoutAttempt next(PayoutAttempt refused, BankAccount account) {
        repository.advance(refused.paymentId(), refused.attempt(), account.bankName(), account.bankAccountNumber());
        return stored(refused.paymentId());
    }

    private PayoutAttempt stored(long paymentId) {
        PayoutAttemptJpaEntity entity = repository.findById(paymentId).orElseThrow();
        return new PayoutAttempt(entity.getPaymentId(), entity.getAttempt(),
                new BankAccount(entity.getBankName(), entity.getBankAccountNumber()));
    }
}
