package com.jaypay.remittance.application.port.in;

import com.jaypay.remittance.domain.Remittance;

import java.util.List;

public interface FindRemittanceUseCase {

    List<Remittance> findRemittanceHistory(String membershipId);
}
