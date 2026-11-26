package com.jaypay.eventflow.domain;

import com.jaypay.common.flow.FlowOutcome;
import com.jaypay.common.flow.FlowStep;
import com.jaypay.common.flow.FlowType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTest {

    private static final Instant START = Instant.parse("2026-11-26T01:00:00Z");

    @Test
    void aFlowWhoseWalletWasIncreasedSucceeded() {
        Flow flow = Flow.of(FlowType.RECHARGE, "r-1", List.of(
                step(FlowStep.RECHARGE_REQUESTED, FlowOutcome.SUCCEEDED, 0),
                step(FlowStep.MEMBERSHIP_CHECKED, FlowOutcome.SUCCEEDED, 10),
                step(FlowStep.BANK_ACCOUNT_CHECKED, FlowOutcome.SUCCEEDED, 20),
                step(FlowStep.BANK_CHARGED, FlowOutcome.SUCCEEDED, 30),
                step(FlowStep.WALLET_INCREASED, FlowOutcome.SUCCEEDED, 40)));

        assertThat(flow.summary().status()).isEqualTo(FlowStatus.SUCCEEDED);
        assertThat(flow.summary().lastStep()).isEqualTo(FlowStep.WALLET_INCREASED);
        assertThat(flow.summary().failedStep()).isNull();
        assertThat(flow.summary().stepCount()).isEqualTo(5);
        assertThat(flow.summary().startedAt()).isEqualTo(START);
        assertThat(flow.summary().updatedAt()).isEqualTo(START.plusMillis(40));
    }

    @Test
    void aFlowThatHasNotReachedItsLastStepIsInProgressEvenWhenAStepWaitsOnTheBank() {
        Flow flow = Flow.of(FlowType.RECHARGE, "r-2", List.of(
                step(FlowStep.RECHARGE_REQUESTED, FlowOutcome.SUCCEEDED, 0),
                step(FlowStep.BANK_CHARGED, FlowOutcome.IN_PROGRESS, 30)));

        assertThat(flow.summary().status()).isEqualTo(FlowStatus.IN_PROGRESS);
        assertThat(flow.summary().lastStep()).isEqualTo(FlowStep.BANK_CHARGED);
    }

    @Test
    void aFlowFailsAtItsFirstFailedStepAndStaysFailedAfterItsRollback() {
        Flow flow = Flow.of(FlowType.RECHARGE, "r-3", List.of(
                step(FlowStep.CHARGE_ROLLED_BACK, FlowOutcome.SUCCEEDED, 60),
                step(FlowStep.RECHARGE_REQUESTED, FlowOutcome.SUCCEEDED, 0),
                step(FlowStep.RECHARGE_FAILED, FlowOutcome.FAILED, 50),
                step(FlowStep.MEMBERSHIP_CHECKED, FlowOutcome.FAILED, 10)));

        assertThat(flow.summary().status()).isEqualTo(FlowStatus.FAILED);
        assertThat(flow.summary().failedStep()).isEqualTo(FlowStep.MEMBERSHIP_CHECKED);
        assertThat(flow.summary().lastStep()).isEqualTo(FlowStep.CHARGE_ROLLED_BACK);
    }

    @Test
    void ordersStepsByTimeAndStepsOfTheSameInstantByTheirPlaceInTheFlow() {
        Flow flow = Flow.of(FlowType.RECHARGE, "r-4", List.of(
                step(FlowStep.MEMBERSHIP_CHECKED, FlowOutcome.SUCCEEDED, 0),
                step(FlowStep.RECHARGE_REQUESTED, FlowOutcome.SUCCEEDED, 0)));

        assertThat(flow.steps()).extracting(RecordedStep::step)
                .containsExactly(FlowStep.RECHARGE_REQUESTED, FlowStep.MEMBERSHIP_CHECKED);
    }

    @Test
    void takesTheMemberAndTheAmountFromWhicheverStepCarriesThem() {
        Flow flow = Flow.of(FlowType.RECHARGE, "r-5", List.of(
                new RecordedStep(FlowStep.MEMBERSHIP_CHECKED, "membership-service", FlowOutcome.SUCCEEDED, null, START, "12", null),
                new RecordedStep(FlowStep.BANK_CHARGED, "banking-service", FlowOutcome.SUCCEEDED, null, START.plusMillis(5), "12", 3000L)));

        assertThat(flow.summary().membershipId()).isEqualTo("12");
        assertThat(flow.summary().amount()).isEqualTo(3000L);
    }

    private static RecordedStep step(FlowStep step, FlowOutcome outcome, long millis) {
        return new RecordedStep(step, "money-service", outcome, outcome == FlowOutcome.FAILED ? "why" : null, START.plusMillis(millis), "12", 700L);
    }
}
