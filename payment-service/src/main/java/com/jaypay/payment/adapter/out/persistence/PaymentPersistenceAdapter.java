package com.jaypay.payment.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.payment.application.port.out.CreatePaymentPort;
import com.jaypay.payment.domain.Payment;
import lombok.RequiredArgsConstructor;


@PersistenceAdapter
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements CreatePaymentPort {

    private final SpringDataPaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public Payment createPayment(String requestMembershipId, String requestPrice, String franchiseId, String franchiseFeeRate) {
        PaymentJpaEntity jpaEntity = paymentRepository.save(
                new PaymentJpaEntity(
                        requestMembershipId,
                        Integer.parseInt(requestPrice),
                        franchiseId,
                        franchiseFeeRate,
                        0, // 0: 승인, 1: 실패, 2: 정산 완료.
                        null
                )
        );
        return paymentMapper.mapToDomainEntity(jpaEntity);
    }
}
