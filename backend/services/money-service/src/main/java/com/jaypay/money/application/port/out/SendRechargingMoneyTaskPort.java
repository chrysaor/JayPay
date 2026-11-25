package com.jaypay.money.application.port.out;

import com.jaypay.common.RechargingMoneyTask;

public interface SendRechargingMoneyTaskPort {

    void sendRechargingMoneyTaskPort(
            RechargingMoneyTask task
    );

}
