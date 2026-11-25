package com.jaypay.remittance.application.port.out;

import com.jaypay.remittance.domain.RemittanceRequest;

import java.util.List;

public interface FindRemittancePort {

    List<RemittanceRequest> findRemittanceHistory(String membershipId);

}
