package com.jaypay.remittance.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.remittance.application.port.in.FindRemittanceCommand;
import com.jaypay.remittance.application.port.in.RequestRemittanceCommand;
import com.jaypay.remittance.application.port.out.FindRemittancePort;
import com.jaypay.remittance.application.port.out.RequestRemittancePort;
import lombok.RequiredArgsConstructor;

import java.util.List;


@PersistenceAdapter
@RequiredArgsConstructor
public class RemittanceRequestPersistenceAdapter implements RequestRemittancePort, FindRemittancePort {

    private final SpringDataRemittanceRequestRepository remittanceRequestRepository;

    @Override
    public RemittanceRequestJpaEntity createRemittanceRequestHistory(RequestRemittanceCommand command) {
        return remittanceRequestRepository.save(RemittanceRequestJpaEntity.builder()
                .fromMembershipId(command.getFromMembershipId())
                .toMembershipId(command.getToMembershipId())
                .toBankName(command.getToBankName())
                .toBankAccountNumber(command.getToBankAccountNumber())
                .amount(command.getAmount())
                .remittanceType(command.getRemittanceType())
                .build()
        );
    }

    @Override
    public boolean saveRemittanceRequestHistory(RemittanceRequestJpaEntity entity) {
        remittanceRequestRepository.save(entity);
        return true;
    }

    @Override
    public List<RemittanceRequestJpaEntity> findRemittanceHistory(FindRemittanceCommand command) {
        // using JPA
        return null;
    }
}
