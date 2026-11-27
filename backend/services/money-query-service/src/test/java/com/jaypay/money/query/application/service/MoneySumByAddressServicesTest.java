package com.jaypay.money.query.application.service;

import com.jaypay.money.query.application.port.in.QueryMoneySumByRegionQuery;
import com.jaypay.money.query.application.port.out.MemberAddressInfo;
import com.jaypay.money.query.domain.MoneySumByRegion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MoneySumByAddressServicesTest {

    private record Increase(String rechargingRequestId, String address, long moneyAmount) {
    }

    @Test
    void recordsAnIncreaseUnderTheAddressOfTheMember() {
        List<Increase> increases = new ArrayList<>();
        IncreaseMoneySumByAddressService service = new IncreaseMoneySumByAddressService(
                membershipId -> Optional.of(new MemberAddressInfo(membershipId, membershipId.equals("7") ? "Seocho" : "unknown")),
                (rechargingRequestId, address, moneyAmount) -> increases.add(new Increase(rechargingRequestId, address, moneyAmount)),
                Duration.ZERO, Duration.ZERO);

        service.increaseMoneySum("recharge-1", "7", 3000);

        assertThat(increases).containsExactly(new Increase("recharge-1", "Seocho", 3000));
    }

    @Test
    void reportsTheStoredSumForTheRequestedAddress() {
        QueryMoneySumByRegionService service = new QueryMoneySumByRegionService(
                address -> address.equals("Seocho") ? 4200 : 0);

        MoneySumByRegion sum = service.queryMoneySumByRegion(new QueryMoneySumByRegionQuery("Seocho"));

        assertThat(sum.getRegionName()).isEqualTo("Seocho");
        assertThat(sum.getMoneySum()).isEqualTo(4200);
    }
}
