package com.jaypay.remittance.application.port.in;

import com.jaypay.remittance.domain.RemittanceRequest;

import java.util.List;


public interface FindRemittanceUseCase {
    List<RemittanceRequest> findRemittanceHistory(FindRemittanceCommand command);
}
