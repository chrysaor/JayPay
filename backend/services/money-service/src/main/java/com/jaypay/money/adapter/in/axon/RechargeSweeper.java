package com.jaypay.money.adapter.in.axon;

import com.jaypay.money.adapter.axon.event.RechargeSweptEvent;
import lombok.RequiredArgsConstructor;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RechargeSweeper {

    private final JdbcTemplate database;
    private final EventGateway eventGateway;

    @Scheduled(fixedDelayString = "${money.recharge.sweep.every}")
    void sweep() {
        database.queryForList(
                        "select association_value from association_value_entry where saga_type = ? and association_key = 'rechargingRequestId'",
                        String.class, MoneyRechargeSaga.class.getName())
                .forEach(rechargingRequestId -> eventGateway.publish(new RechargeSweptEvent(rechargingRequestId)));
    }
}
