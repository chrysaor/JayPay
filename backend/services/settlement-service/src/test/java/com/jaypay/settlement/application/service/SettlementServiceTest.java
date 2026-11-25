package com.jaypay.settlement.application.service;

import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementServiceTest {

    private static final BankAccount FIRST_FRANCHISE_ACCOUNT = new BankAccount("jaybank", "100-1");
    private static final BankAccount SECOND_FRANCHISE_ACCOUNT = new BankAccount("otherbank", "200-2");

    private final RecordingBanking banking = new RecordingBanking(Map.of(
            "franchise-1", FIRST_FRANCHISE_ACCOUNT,
            "franchise-2", SECOND_FRANCHISE_ACCOUNT));

    @Test
    void sendsOneFirmBankingPerFranchiseAndFinishesEveryPayment() {
        RecordingPayments payments = new RecordingPayments(List.of(
                new Payment(1, "franchise-1", 1000, "10"),
                new Payment(2, "franchise-2", 500, "3.5"),
                new Payment(3, "franchise-1", 200, "10")));

        new SettlementService(banking, payments).settleNormalStatusPayments();

        assertThat(banking.firmBankings).containsExactlyInAnyOrder(
                new FirmBanking(FIRST_FRANCHISE_ACCOUNT, 9_000_000 + 1_800_000),
                new FirmBanking(SECOND_FRANCHISE_ACCOUNT, 4_825_000));
        assertThat(payments.finished).containsExactly(1L, 2L, 3L);
    }

    @Test
    void doesNothingWhenNoPaymentWaitsForSettlement() {
        RecordingPayments payments = new RecordingPayments(List.of());

        new SettlementService(banking, payments).settleNormalStatusPayments();

        assertThat(banking.firmBankings).isEmpty();
        assertThat(payments.finished).isEmpty();
    }

    private record FirmBanking(BankAccount to, int moneyAmount) {
    }

    private static final class RecordingBanking implements BankingPort {
        private final Map<String, BankAccount> accounts;
        private final List<FirmBanking> firmBankings = new ArrayList<>();

        RecordingBanking(Map<String, BankAccount> accounts) {
            this.accounts = accounts;
        }

        @Override
        public BankAccount getRegisteredBankAccount(String membershipId) {
            return accounts.get(membershipId);
        }

        @Override
        public void requestFirmBanking(BankAccount to, int moneyAmount) {
            firmBankings.add(new FirmBanking(to, moneyAmount));
        }
    }

    private static final class RecordingPayments implements PaymentPort {
        private final List<Payment> normalStatusPayments;
        private final List<Long> finished = new ArrayList<>();

        RecordingPayments(List<Payment> normalStatusPayments) {
            this.normalStatusPayments = normalStatusPayments;
        }

        @Override
        public List<Payment> getNormalStatusPayments() {
            return normalStatusPayments;
        }

        @Override
        public void finishSettlement(long paymentId) {
            finished.add(paymentId);
        }
    }
}
