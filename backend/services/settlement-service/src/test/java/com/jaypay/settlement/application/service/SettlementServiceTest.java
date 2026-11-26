package com.jaypay.settlement.application.service;

import com.jaypay.settlement.application.port.out.BankAccount;
import com.jaypay.settlement.application.port.out.BankingPort;
import com.jaypay.settlement.application.port.out.Payout;
import com.jaypay.settlement.application.port.out.Payment;
import com.jaypay.settlement.application.port.out.PaymentPort;
import com.jaypay.settlement.application.port.out.SettlementFlowPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final RecordingFlow flow = new RecordingFlow();

    @Test
    void reportsTheStartThePayoutAndTheSettlementOfEachPayment() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700));

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(flow.reports).containsExactly("started 1", "paid out 1 PAID", "settled 1");
    }

    @Test
    void reportsAPaymentWaitingForItsMerchantsAccountAndARefusedPayoutWithoutSettlingThem() {
        PaidPayments payments = new PaidPayments(new Payment(1, "99", 9700), new Payment(2, "11", 4825));
        banking.refuse(FIRST_MERCHANT_ACCOUNT);

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(flow.reports).containsExactly("waiting for an account 1", "started 2", "paid out 2 REFUSED");
    }

    @Test
    void reportsAPayoutWithoutAnAnswerOnlyOnceTheBankAnswers() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700));
        banking.silence(FIRST_MERCHANT_ACCOUNT);
        SettlementService settlement = new SettlementService(banking, payments, flow);

        settlement.settleNormalStatusPayments();
        banking.answerAgain();
        settlement.settleNormalStatusPayments();

        assertThat(flow.reports).containsExactly("started 1", "paid out 1 UNCONFIRMED", "started 1", "paid out 1 PAID", "settled 1");
    }

    @Test
    void doesNotReportASettlementThatAnotherRunFinished() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700));
        payments.settledElsewhere(1);

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(flow.reports).containsExactly("started 1", "paid out 1 PAID");
    }

    @Test
    void paysEachPaidPaymentItsPayoutOnceAndSettlesIt() {
        PaidPayments payments = new PaidPayments(
                new Payment(1, "11", 9700),
                new Payment(2, "12", 4825),
                new Payment(3, "11", 194));

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(
                new Transfer(FIRST_MERCHANT_ACCOUNT, 9700),
                new Transfer(SECOND_MERCHANT_ACCOUNT, 4825),
                new Transfer(FIRST_MERCHANT_ACCOUNT, 194));
        assertThat(payments.settled).containsExactly(1L, 2L, 3L);
    }

    @Test
    void aSecondRunPaysNothingAgain() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        SettlementService settlement = new SettlementService(banking, payments, flow);

        settlement.settleNormalStatusPayments();
        settlement.settleNormalStatusPayments();

        assertThat(banking.transfers).hasSize(2);
    }

    @Test
    void paysAPaymentThatAnotherRunAlreadyPaidUnderTheSameIdOnlyOnce() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        banking.alreadyPaid("settlement-1", FIRST_MERCHANT_ACCOUNT, 9700);

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(new Transfer(FIRST_MERCHANT_ACCOUNT, 9700), new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
        assertThat(payments.settled).containsExactly(1L, 2L);
    }

    @Test
    void leavesAPaymentUnsettledWhileItsMerchantHasNoAccount() {
        PaidPayments payments = new PaidPayments(new Payment(1, "99", 9700), new Payment(2, "12", 4825));

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
        assertThat(payments.paid()).extracting(Payment::paymentId).containsExactly(1L);
    }

    @Test
    void aPayoutWithoutAnAnswerStaysPaidAndTheNextRunFinishesItOnce() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        banking.silence(FIRST_MERCHANT_ACCOUNT);
        SettlementService settlement = new SettlementService(banking, payments, flow);

        settlement.settleNormalStatusPayments();
        assertThat(payments.paid()).extracting(Payment::paymentId).containsExactly(1L);
        assertThat(payments.settled).containsExactly(2L);

        banking.answerAgain();
        settlement.settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactlyInAnyOrder(new Transfer(FIRST_MERCHANT_ACCOUNT, 9700), new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
        assertThat(banking.askedIds).containsExactly("settlement-1", "settlement-2", "settlement-1");
        assertThat(payments.settled).containsExactly(2L, 1L);
    }

    @Test
    void aRefusedPayoutIsNeitherSettledNorPaidAgain() {
        PaidPayments payments = new PaidPayments(new Payment(1, "11", 9700), new Payment(2, "12", 4825));
        banking.refuse(FIRST_MERCHANT_ACCOUNT);
        SettlementService settlement = new SettlementService(banking, payments, flow);

        settlement.settleNormalStatusPayments();
        settlement.settleNormalStatusPayments();

        assertThat(banking.transfers).containsExactly(new Transfer(SECOND_MERCHANT_ACCOUNT, 4825));
        assertThat(payments.paid()).extracting(Payment::paymentId).containsExactly(1L);
        assertThat(payments.settled).containsExactly(2L);
    }

    @Test
    void doesNothingWhenNoPaymentWaitsForSettlement() {
        PaidPayments payments = new PaidPayments();

        new SettlementService(banking, payments, flow).settleNormalStatusPayments();

        assertThat(banking.transfers).isEmpty();
        assertThat(payments.settled).isEmpty();
    }

    private record Transfer(BankAccount to, long amount) {
    }

    private static final class RecordingBanking implements BankingPort {
        private final Map<String, BankAccount> accounts;
        private final Set<BankAccount> silent = new HashSet<>();
        private final Set<BankAccount> refused = new HashSet<>();
        private final Map<String, Payout> answered = new HashMap<>();
        private final List<Transfer> transfers = new ArrayList<>();
        private final List<String> askedIds = new ArrayList<>();

        RecordingBanking(Map<String, BankAccount> accounts) {
            this.accounts = accounts;
        }

        void silence(BankAccount account) {
            silent.add(account);
        }

        void refuse(BankAccount account) {
            refused.add(account);
        }

        void answerAgain() {
            silent.clear();
        }

        void alreadyPaid(String requestId, BankAccount to, long amount) {
            answered.put(requestId, Payout.PAID);
            transfers.add(new Transfer(to, amount));
        }

        @Override
        public Optional<BankAccount> getRegisteredBankAccount(String membershipId) {
            return Optional.ofNullable(accounts.get(membershipId));
        }

        @Override
        public Payout pay(String requestId, BankAccount to, long amount) {
            askedIds.add(requestId);
            if (silent.contains(to)) {
                return Payout.UNCONFIRMED;
            }
            return answered.computeIfAbsent(requestId, id -> {
                if (refused.contains(to)) {
                    return Payout.REFUSED;
                }
                transfers.add(new Transfer(to, amount));
                return Payout.PAID;
            });
        }
    }

    private static final class RecordingFlow implements SettlementFlowPort {
        private final List<String> reports = new ArrayList<>();

        @Override
        public void waitingForAccount(Payment payment) {
            reports.add("waiting for an account " + payment.paymentId());
        }

        @Override
        public void started(Payment payment) {
            reports.add("started " + payment.paymentId());
        }

        @Override
        public void paidOut(Payment payment, Payout payout) {
            reports.add("paid out " + payment.paymentId() + " " + payout);
        }

        @Override
        public void settled(Payment payment) {
            reports.add("settled " + payment.paymentId());
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
