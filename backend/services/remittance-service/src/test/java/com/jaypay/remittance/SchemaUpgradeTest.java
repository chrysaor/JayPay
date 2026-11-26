package com.jaypay.remittance;

import com.jaypay.schema.SchemaUpgradeContract;

import java.util.List;

class SchemaUpgradeTest extends SchemaUpgradeContract {

    @Override
    protected List<String> enumColumns() {
        return List.of("remittance.remittance_status");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of("remittance_amount_positive", "remittance_receiver_matches_type");
    }
}
