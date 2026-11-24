package com.jaypay.remittance.application.port.out;

import com.jaypay.remittance.domain.RemittanceRequest;

public interface RequestRemittancePort {

    long createRemittanceRequestHistory(NewRemittanceRequest request);
    RemittanceRequest saveRemittanceRequestStatus(long remittanceRequestHistoryId, String remittanceStatus);

}
