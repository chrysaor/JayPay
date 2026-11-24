package com.jaypay.remittance.application.port.out;

import com.jaypay.remittance.adapter.out.persistence.RemittanceRequestJpaEntity;
import com.jaypay.remittance.application.port.in.RequestRemittanceCommand;

public interface RequestRemittancePort {

    RemittanceRequestJpaEntity createRemittanceRequestHistory(RequestRemittanceCommand command);
    boolean saveRemittanceRequestHistory(RemittanceRequestJpaEntity entity);

}
