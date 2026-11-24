package com.jaypay.remittance.application.service;


import com.jaypay.common.UseCase;
import com.jaypay.remittance.adapter.out.persistence.RemittanceRequestMapper;
import com.jaypay.remittance.application.port.in.FindRemittanceCommand;
import com.jaypay.remittance.application.port.in.FindRemittanceUseCase;
import com.jaypay.remittance.application.port.out.FindRemittancePort;
import com.jaypay.remittance.domain.RemittanceRequest;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;
import java.util.List;

@UseCase
@RequiredArgsConstructor
@Transactional
public class FindRemittanceService implements FindRemittanceUseCase {

    private final FindRemittancePort findRemittancePort;
    private final RemittanceRequestMapper mapper;

    @Override
    public List<RemittanceRequest> findRemittanceHistory(FindRemittanceCommand command) {
        findRemittancePort.findRemittanceHistory(command);
        return null;
    }
}
