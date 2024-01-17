package com.jaypay.remittance.application.port.out;
import com.jaypay.remittance.adapter.out.persistence.RemittanceRequestJpaEntity;
import com.jaypay.remittance.application.port.in.FindRemittanceCommand;

import java.util.List;

public interface FindRemittancePort {

    List<RemittanceRequestJpaEntity> findRemittanceHistory(FindRemittanceCommand command);

}
