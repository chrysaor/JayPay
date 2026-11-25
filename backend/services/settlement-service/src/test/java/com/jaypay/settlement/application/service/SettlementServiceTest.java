package com.jaypay.settlement.application.service;

import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementServiceTest {

    private static final BankAccount FIRST_MERCHANT_ACCOUNT = new BankAccount("jaybank", "100-1");
    private static final BankAccount SECOND_MERCHANT_ACCOUNT = new BankAccount("otherbank", "200-2");

    private final RecordingBanking banking = new RecordingBanking(Map.of(
            "11", FIRST_MERCHANT_ACCOUNT,
            "12", SECOND_MERCHANT_ACCOUNT));

    @Test
    void paysEachPaidPaymentItsPayoutOnceAndSettlesIt() {
        PaidPayments payments = new PaidPayments(
                new Payment(1, "11", 9700),
                new Payment(2, "12", 4825),
                new Payment(3, "11", 194));

        new SettlementService(banking, payments).settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(
                new Transfer(FIRST_MERCHANT_ACCOUNT, 9700),
                new Transfer(SECOND_MERCHANT_ACCOUNT, 4825),
                new Transfer(FIRST_MERCHANT_ACCOUNT, 194));
        assertThat(payments.settled).containsExactly(1L, 2L, 3L);
    }

    @Test
    void aSecondRunPaysNothingAgain() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        SettlementService settlement = new SettlementService(banking, payments);

        settlement.settleNormalStatusPayments();
        settlement.settleNormalStatusPayments();

        assertThat(banking.transfers).hasSize(2);
    }

    @Test
    void doesNotPayAPaymentThatAnotherRunSettledFirst() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        payments.settledElsewhere(1);

        new SettlementService(banking, payments).settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
    }

    @Test
    void leavesAPaymentUnsettledWhileItsMerchantHasNoAccount() {
        PaidPayments payments = new PaidPayments(new Payment(1, "99", 9700), new Payment(2, "12", 4825));

        new SettlementService(banking, payments).settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
        assertThat(payments.paid()).extracting(Payment::paymentId).containsExactly(1L);
    }

    @Test
    void aFailedTransferNeitherStopsTheOthersNorIsPaidAgain() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        banking.failTransfersTo(FIRST_MERCHANT_ACCOUNT);
        SettlementService settlement = new SettlementService(banking, payments);

        settlement.settleNormalStatusPayments();
        settlement.settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
        assertThat(banking.attempts).isEqualTo(2);
    }

    @Test
    void doesNothingWhenNoPaymentWaitsForSettlement() {
        PaidPayments payments = new PaidPayments();

        new SettlementService(banking, payments).settleNormalStatusPayments();

        assertThat(banking.transfers).isEmpty();
        assertThat(payments.settled).isEmpty();
    }

    private record Transfer(BankAccount to, long amount) {
    }

    private static final class RecordingBanking implements BankingPort {
        private final Map<String, BankAccount> accounts;
        private final Set<BankAccount> failing = new HashSet<>();
        private final List<Transfer> transfers = new ArrayList<>();
        private int attempts;

        RecordingBanking(Map<String, BankAccount> accounts) {
            this.accounts = accounts;
        }

        void failTransfersTo(BankAccount account) {
            failing.add(account);
        }

        @Override
        public Optional<BankAccount> getRegisteredBankAccount(String membershipId) {
            return Optional.ofNullable(accounts.get(membershipId));
        }

        @Override
        public boolean requestFirmBanking(BankAccount to, long amount) {
            attempts++;
            if (failing.contains(to)) {
                throw new IllegalStateException("the bank did not answer");
            }
            transfers.add(new Transfer(to, amount));
            return true;
        }
    }

    private static final class PaidPayments implements PaymentPort {
        private final Map<Long, Payment> paid = new LinkedHashMap<>();
        private final Map<Long, Payment> staleInTheList = new LinkedHashMap<>();
        private final List<Long> settled = new ArrayList<>();

        PaidPayments(Payment... payments) {
            for (Payment payment : payments) {
                paid.put(payment.paymentId(), payment);
            }
        }

        void settledElsewhere(long paymentId) {
            staleInTheList.put(paymentId, paid.remove(paymentId));
        }

        List<Payment> paid() {
            return List.copyOf(paid.values());
        }

        @Override
        public List<Payment> getPaidPayments() {
            List<Payment> listed = new ArrayList<>(staleInTheList.values());
            listed.addAll(paid.values());
            return listed;
        }

        @Override
        public boolean finishSettlement(long paymentId) {
            if (paid.remove(paymentId) == null) {
                return false;
            }
            settled.add(paymentId);
            return true;
        }
    }
}
