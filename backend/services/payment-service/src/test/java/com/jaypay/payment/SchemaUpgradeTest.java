package com.jaypay.payment;

import com.jaypay.schema.SchemaUpgradeContract;

import java.util.List;

class SchemaUpgradeTest extends SchemaUpgradeContract {

    @Override
    protected List<String> enumColumns() {
        return List.of("payment.payment_status");
    }

    @Override
    protected List<String> checksThatStay() {
        return List.of("payment_price_positive", "payment_fee_rate_in_range", "payment_fee_and_payout_make_the_price");
    }
}
