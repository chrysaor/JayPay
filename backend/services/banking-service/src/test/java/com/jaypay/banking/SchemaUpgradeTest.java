package com.jaypay.banking;

import com.jaypay.schema.SchemaUpgradeContract;

import java.util.List;

class SchemaUpgradeTest extends SchemaUpgradeContract {

    @Override
    protected List<String> enumColumns() {
        return List.of("bank_transfer.status");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of();
    }
}
