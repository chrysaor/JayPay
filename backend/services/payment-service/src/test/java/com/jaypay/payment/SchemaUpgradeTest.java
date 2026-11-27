package com.jaypay.payment;

import com.jaypay.payment.application.port.in.ResumeUnconfirmedPaymentsUseCase;
import com.jaypay.schema.SchemaUpgradeContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaUpgradeTest extends SchemaUpgradeContract {

    @Autowired
    private JdbcTemplate database;

    @Autowired
    private ResumeUnconfirmedPaymentsUseCase sweep;

    @Test
    void aPaymentStoredByTheLastReleaseHasNoSweepsAndTheSweepRuns() {
        sweep.resumeUnconfirmedPayments();

        assertThat(database.queryForObject("select sweep_attempts from payment where request_id = 'upgrade-1'", Integer.class)).isZero();
    }

    @Override
    protected List<String> enumColumns() {
        return List.of("payment.payment_status");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of("payment_price_positive", "payment_fee_rate_in_range", "payment_fee_and_payout_make_the_price");
    }
}
