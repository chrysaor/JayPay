package com.jaypay.payment.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.payment.application.port.out.PaymentPort;
import com.jaypay.payment.domain.Charge;
import com.jaypay.payment.domain.FeeRate;
import com.jaypay.payment.domain.NewPayment;
import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentStatus;
import com.jaypay.payment.domain.RequestId;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@PersistenceAdapter
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentPort {

    private final SpringDataPaymentRepository paymentRepository;

    @Override
    public Payment claim(NewPayment payment) {
        paymentRepository.claim(
                payment.requestId().value(),
                Long.parseLong(payment.requestMembershipId()),
                Long.parseLong(payment.franchiseId()),
                payment.requestPrice(),
                payment.feeRate().basisPoints(),
                payment.charge().fee(),
                payment.charge().payout());
        return paymentRepository.findByRequestId(payment.requestId().value()).map(PaymentPersistenceAdapter::toDomain).orElseThrow();
    }

    @Override
    public Payment conclude(long paymentId, PaymentStatus outcome) {
        paymentRepository.conclude(paymentId, outcome.name());
        return find(paymentId).orElseThrow();
    }

    @Override
    public boolean settle(long paymentId) {
        return paymentRepository.settle(paymentId) == 1;
    }

    @Override
    public Optional<Payment> find(long paymentId) {
        return paymentRepository.findById(paymentId).map(PaymentPersistenceAdapter::toDomain);
    }

    @Override
    public List<Payment> findPaid() {
        return paymentRepository.findByPaymentStatusOrderByPaymentId(PaymentStatus.PAID).stream()
                .map(PaymentPersistenceAdapter::toDomain)
                .toList();
    }

    private static Payment toDomain(PaymentJpaEntity entity) {
        return new Payment(
                entity.getPaymentId(),
                new RequestId(entity.getRequestId()),
                entity.getRequestMembershipId().toString(),
                entity.getFranchiseId().toString(),
                entity.getRequestPrice(),
                new FeeRate(entity.getFeeRateBasisPoints()),
                new Charge(entity.getFee(), entity.getPayout()),
                entity.getPaymentStatus(),
                entity.getApprovedAt());
    }
}
