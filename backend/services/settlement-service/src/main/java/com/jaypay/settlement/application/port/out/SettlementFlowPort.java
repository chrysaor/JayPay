package com.jaypay.settlement.application.port.out;

public interface SettlementFlowPort {

    void waitingForAccount(Payment payment);

    void started(Payment payment);

    void paidOut(Payment payment, Payout payout);

    void settled(Payment payment);
}
