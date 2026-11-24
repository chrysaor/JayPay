package com.jaypay.remittance.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.remittance.application.port.out.FindRemittancePort;
import com.jaypay.remittance.application.port.out.NewRemittanceRequest;
import com.jaypay.remittance.application.port.out.RequestRemittancePort;
import com.jaypay.remittance.domain.RemittanceRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;


@PersistenceAdapter
@RequiredArgsConstructor
public class RemittanceRequestPersistenceAdapter implements RequestRemittancePort, FindRemittancePort {

    private final SpringDataRemittanceRequestRepository remittanceRequestRepository;

    @Override
    public long createRemittanceRequestHistory(NewRemittanceRequest request) {
        return remittanceRequestRepository.save(RemittanceRequestJpaEntity.builder()
                .fromMembershipId(request.fromMembershipId())
                .toMembershipId(request.toMembershipId())
                .toBankName(request.toBankName())
                .toBankAccountNumber(request.toBankAccountNumber())
                .amount(request.amount())
                .remittanceType(request.remittanceType())
                .build()
        ).getRemittanceRequestId();
    }

    @Override
    public RemittanceRequest saveRemittanceRequestStatus(long remittanceRequestHistoryId, String remittanceStatus) {
        RemittanceRequestJpaEntity entity = remittanceRequestRepository.getReferenceById(remittanceRequestHistoryId);
        entity.setRemittanceStatus(remittanceStatus);
        return toDomain(remittanceRequestRepository.save(entity));
    }

    @Override
    public List<RemittanceRequest> findRemittanceHistory(String membershipId) {
        return null;
    }

    private static RemittanceRequest toDomain(RemittanceRequestJpaEntity entity) {
        return RemittanceRequest.generateRemittanceRequest(
                new RemittanceRequest.RemittanceRequestId(entity.getFromMembershipId()),
                new RemittanceRequest.RemittanceFromMembershipId(entity.getFromMembershipId()),
                new RemittanceRequest.ToBankName(entity.getToBankName()),
                new RemittanceRequest.ToBankAccountNumber(entity.getToBankAccountNumber()),
                new RemittanceRequest.RemittanceType(entity.getRemittanceType()),
                new RemittanceRequest.Amount(entity.getAmount()),
                new RemittanceRequest.RemittanceStatus(entity.getRemittanceStatus())
        );
    }
}
