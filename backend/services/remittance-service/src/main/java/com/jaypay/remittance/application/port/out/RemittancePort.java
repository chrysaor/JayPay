package com.jaypay.remittance.application.port.out;

import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceStatus;
import com.jaypay.remittance.domain.RequestId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemittancePort {

    Optional<Remittance> findByRequestId(RequestId requestId);

    Remittance claim(NewRemittance remittance);

    boolean advance(UUID remittanceId, RemittanceStatus from, RemittanceStatus to);

    Remittance find(UUID remittanceId);

    List<Remittance> findSentOrReceivedBy(String membershipId);
}
