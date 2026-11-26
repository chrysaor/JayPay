package com.jaypay.eventflow;

import com.jaypay.schema.SchemaUpgradeContract;

import java.util.List;

class SchemaUpgradeTest extends SchemaUpgradeContract {

    @Override
    protected List<String> enumColumns() {
        return List.of("flow.status", "flow_step.outcome");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of();
    }
}
