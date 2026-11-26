package com.jaypay.remittance.application.port.in;

import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Remittance;

public interface RequestRemittanceUseCase {

    Remittance requestRemittance(NewRemittance remittance);
}
