package com.jaypay.money.query.adapter.in.axon;

import com.jaypay.common.event.RequestFirmBankingFinishedEvent;
import com.jaypay.money.query.application.port.in.IncreaseMoneySumByAddressUseCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyIncreaseEventHandlerTest {

    private final List<String> increases = new ArrayList<>();
    private final MoneyIncreaseEventHandler handler = new MoneyIncreaseEventHandler(recording());

    @Test
    void addsARechargeTheBankConfirmedToTheRegionTotal() {
        handler.handler(finished("7", 3000, RequestFirmBankingFinishedEvent.SUCCEEDED));

        assertThat(increases).containsExactly("7:3000");
    }

    @Test
    void leavesARefusedOrUnconfirmedRechargeOutOfTheRegionTotal() {
        handler.handler(finished("7", 3000, RequestFirmBankingFinishedEvent.REFUSED));
        handler.handler(finished("7", 4000, RequestFirmBankingFinishedEvent.UNCONFIRMED));

        assertThat(increases).isEmpty();
    }

    private IncreaseMoneySumByAddressUseCase recording() {
        return (membershipId, moneyAmount) -> increases.add(membershipId + ":" + moneyAmount);
    }

    private static RequestFirmBankingFinishedEvent finished(String membershipId, long amount, int status) {
        return new RequestFirmBankingFinishedEvent("firmbanking-1", "recharge-1", membershipId, "jaypay-company", "123456789", amount, status, "aggregate-1");
    }
}
