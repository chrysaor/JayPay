package com.jaypay.remittance.application.port.out;

import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceRejectedException.Reason;
import com.jaypay.remittance.domain.RemittanceStatus;

public interface RemittanceFlowPort {

    void refused(NewRemittance request, Reason reason);

    void requested(Remittance remittance);

    void advanced(Remittance remittance, RemittanceStatus next);
}
