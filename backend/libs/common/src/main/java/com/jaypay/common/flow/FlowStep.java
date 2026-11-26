package com.jaypay.common.flow;

public enum FlowStep {
    RECHARGE_REQUESTED(FlowType.RECHARGE, false),
    MEMBERSHIP_CHECKED(FlowType.RECHARGE, false),
    BANK_ACCOUNT_CHECKED(FlowType.RECHARGE, false),
    BANK_CHARGED(FlowType.RECHARGE, false),
    WALLET_INCREASED(FlowType.RECHARGE, true),
    RECHARGE_FAILED(FlowType.RECHARGE, false),
    CHARGE_ROLLED_BACK(FlowType.RECHARGE, false),
    REMITTANCE_REQUESTED(FlowType.REMITTANCE, false),
    SENDER_DEBITED(FlowType.REMITTANCE, false),
    RECEIVER_CREDITED(FlowType.REMITTANCE, true),
    BANK_TRANSFERRED(FlowType.REMITTANCE, true),
    SENDER_REFUNDED(FlowType.REMITTANCE, false),
    PAYMENT_REQUESTED(FlowType.PAYMENT, false),
    PAYER_DEBITED(FlowType.PAYMENT, true),
    SETTLEMENT_STARTED(FlowType.SETTLEMENT, false),
    MERCHANT_PAID_OUT(FlowType.SETTLEMENT, false),
    PAYMENT_SETTLED(FlowType.SETTLEMENT, true);

    private final FlowType flowType;
    private final boolean completesFlow;

    FlowStep(FlowType flowType, boolean completesFlow) {
        this.flowType = flowType;
        this.completesFlow = completesFlow;
    }

    public FlowType flowType() {
        return flowType;
    }

    public boolean completesFlow() {
        return completesFlow;
    }
}
