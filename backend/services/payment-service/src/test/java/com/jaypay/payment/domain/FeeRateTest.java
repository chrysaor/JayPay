package com.jaypay.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FeeRateTest {

    @ParameterizedTest
    @CsvSource({
            "300, 1, 0, 1",
            "300, 33, 0, 33",
            "300, 34, 1, 33",
            "300, 999, 29, 970",
            "300, 1000, 30, 970",
            "250, 3000, 75, 2925",
            "1, 9999, 0, 9999",
            "1, 10000, 1, 9999",
            "300, 10000000, 300000, 9700000",
            "0, 10000000, 0, 10000000",
            "10000, 10000000, 10000000, 0",
            "9999, 1, 0, 1"
    })
    void roundsTheFeeDownToAWholeWonAndPaysTheMerchantTheRest(int basisPoints, long amount, long fee, long payout) {
        Charge charge = new FeeRate(basisPoints).charge(amount);

        assertThat(charge.fee()).isEqualTo(fee);
        assertThat(charge.payout()).isEqualTo(payout);
    }

    @Test
    void feeAndPayoutAlwaysAddUpToTheAmount() {
        for (int basisPoints : new int[]{0, 1, 7, 250, 300, 333, 9999, 10000}) {
            for (long amount = 1; amount <= 20_000; amount++) {
                Charge charge = new FeeRate(basisPoints).charge(amount);
                assertThat(charge.fee() + charge.payout()).isEqualTo(amount);
                assertThat(charge.fee()).isBetween(0L, amount);
            }
        }
    }

    @Test
    void doesNotOverflowAtTheLargestAmount() {
        Charge charge = new FeeRate(10000).charge(Long.MAX_VALUE);

        assertThat(charge.fee()).isEqualTo(Long.MAX_VALUE);
        assertThat(charge.payout()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10001, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void refusesARateBelowZeroOrAboveOneHundredPercent(int basisPoints) {
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeRate(basisPoints));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void refusesAnAmountThatIsNotPositive(long amount) {
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeRate(300).charge(amount));
    }
}
