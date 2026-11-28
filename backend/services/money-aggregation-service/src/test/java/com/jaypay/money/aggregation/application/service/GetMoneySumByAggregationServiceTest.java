package com.jaypay.money.aggregation.application.service;

import com.jaypay.money.aggregation.application.port.in.GetMoneySumByAddressCommand;
import com.jaypay.money.aggregation.application.port.out.MemberMoney;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GetMoneySumByAggregationServiceTest {

    private final List<List<String>> askedFor = new ArrayList<>();

    @Test
    void addsUpTheBalancesOfEveryMemberAtTheAddressAskingForAtMostAHundredAtATime() {
        List<String> members = IntStream.rangeClosed(1, 250).mapToObj(String::valueOf).toList();

        long sum = serviceFor(members).getMoneySumByAddress(new GetMoneySumByAddressCommand("seoul"));

        assertThat(sum).isEqualTo(250L * 3_000_000_000L);
        assertThat(askedFor).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(100));
        assertThat(askedFor.stream().flatMap(List::stream)).containsExactlyInAnyOrderElementsOf(members);
    }

    @Test
    void anAddressWithoutMembersSumsToZeroWithoutAskingForBalances() {
        long sum = serviceFor(List.of()).getMoneySumByAddress(new GetMoneySumByAddressCommand("nowhere"));

        assertThat(sum).isZero();
        assertThat(askedFor).isEmpty();
    }

    private GetMoneySumByAggregationService serviceFor(List<String> members) {
        return new GetMoneySumByAggregationService(
                ids -> {
                    askedFor.add(ids);
                    return ids.stream().map(id -> new MemberMoney(id, 3_000_000_000L)).toList();
                },
                address -> members);
    }
}
