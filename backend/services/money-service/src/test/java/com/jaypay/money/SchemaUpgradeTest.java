package com.jaypay.money;

import com.jaypay.schema.SchemaUpgradeContract;

import java.util.List;

class SchemaUpgradeTest extends SchemaUpgradeContract {

    @Override
    protected List<String> enumColumns() {
        return List.of("wallet_change.change_type", "wallet_change.status");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of("wallet_balance_not_negative");
    }
}
