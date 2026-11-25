package com.jaypay.money.adapter.in.axon;

import com.jaypay.money.adapter.axon.event.IncreaseMemberMoneyEvent;
import com.jaypay.money.adapter.axon.event.MemberMoneyCreatedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberMoneyAggregateTest {

    @Test
    void replayingIncreasesAddsThemUp() {
        MemberMoneyAggregate aggregate = new MemberMoneyAggregate();

        aggregate.on(new MemberMoneyCreatedEvent("7"));
        aggregate.on(new IncreaseMemberMoneyEvent("aggregate-7", "7", 1000));
        aggregate.on(new IncreaseMemberMoneyEvent("aggregate-7", "7", 2500));

        assertThat(aggregate.getBalance()).isEqualTo(3500L);
    }
}
