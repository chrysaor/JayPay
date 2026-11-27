package com.jaypay.remittance;

import com.jaypay.remittance.application.port.in.ResumeUnfinishedRemittancesUseCase;
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
    private ResumeUnfinishedRemittancesUseCase sweep;

    @Test
    void aRemittanceStoredByTheLastReleaseHasNoSweepsAndTheSweepRuns() {
        sweep.resumeUnfinishedRemittances();

        assertThat(database.queryForObject("select sweep_attempts from remittance where request_id = 'upgrade-1'", Integer.class)).isZero();
    }

    @Override
    protected List<String> enumColumns() {
        return List.of("remittance.remittance_status");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of("remittance_amount_positive", "remittance_receiver_matches_type");
    }
}
