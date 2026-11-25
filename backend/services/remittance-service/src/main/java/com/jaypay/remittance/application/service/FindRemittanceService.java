package com.jaypay.remittance.application.service;


import com.jaypay.common.UseCase;
import com.jaypay.remittance.application.port.in.FindRemittanceCommand;
import com.jaypay.remittance.application.port.in.FindRemittanceUseCase;
import com.jaypay.remittance.application.port.out.FindRemittancePort;
import com.jaypay.remittance.domain.RemittanceRequest;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.List;

@UseCase
@RequiredArgsConstructor
@Transactional
public class FindRemittanceService implements FindRemittanceUseCase {

    private final FindRemittancePort findRemittancePort;

    @Override
    public List<RemittanceRequest> findRemittanceHistory(FindRemittanceCommand command) {
        findRemittancePort.findRemittanceHistory(command.membershipId());
        return null;
    }
}
