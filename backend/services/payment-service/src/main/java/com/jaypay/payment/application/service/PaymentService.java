package com.jaypay.payment.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.payment.application.port.in.FinishSettlementUseCase;
import com.jaypay.payment.application.port.in.RequestPaymentCommand;
import com.jaypay.payment.application.port.in.RequestPaymentUseCase;
import com.jaypay.payment.application.port.in.SettlementOutcome;
import com.jaypay.payment.application.port.out.DecreaseMoneyPort;
import com.jaypay.payment.application.port.out.GetMembershipPort;
import com.jaypay.payment.application.port.out.MembershipStatus;
import com.jaypay.payment.application.port.out.PaymentFlowPort;
import com.jaypay.payment.application.port.out.PaymentPort;
import com.jaypay.payment.domain.FeeRate;
import com.jaypay.payment.domain.NewPayment;
import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentRejectedException;
import com.jaypay.payment.domain.PaymentRejectedException.Reason;
import com.jaypay.payment.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@UseCase
public class PaymentService implements RequestPaymentUseCase, FinishSettlementUseCase {

    private final PaymentPort paymentPort;
    private final GetMembershipPort getMembershipPort;
    private final DecreaseMoneyPort decreaseMoneyPort;
    private final PaymentFlowPort paymentFlowPort;
    private final FeeRate feeRate;

    public PaymentService(PaymentPort paymentPort,
                          GetMembershipPort getMembershipPort,
                          DecreaseMoneyPort decreaseMoneyPort,
                          PaymentFlowPort paymentFlowPort,
                          @Value("${payment.fee-rate-basis-points}") int feeRateBasisPoints) {
        this.paymentPort = paymentPort;
        this.getMembershipPort = getMembershipPort;
        this.decreaseMoneyPort = decreaseMoneyPort;
        this.paymentFlowPort = paymentFlowPort;
        this.feeRate = new FeeRate(feeRateBasisPoints);
    }

    @Override
    public Payment requestPayment(RequestPaymentCommand command) {
        NewPayment requested = NewPayment.of(command.requestId(), command.requestMembershipId(), command.franchiseId(),
                command.requestPrice(), feeRate);
        Payment payment = paymentPort.findByRequestId(requested.requestId()).orElseGet(() -> claim(requested));
        if (!payment.sameRequestAs(requested)) {
            throw new PaymentRejectedException(Reason.REQUEST_ID_REUSED);
        }
        if (payment.paymentStatus() != PaymentStatus.REQUESTED) {
            return payment;
        }

        Payment concluded = switch (decreaseMoneyPort.decreaseMoney(payment.debitRequestId(), payment.requestMembershipId(), payment.requestPrice())) {
            case SUCCEEDED -> paymentPort.conclude(payment.paymentId(), PaymentStatus.PAID);
            case FAILED -> paymentPort.conclude(payment.paymentId(), PaymentStatus.FAILED);
            case UNCONFIRMED -> throw new PaymentRejectedException(Reason.PAYMENT_NOT_CONFIRMED);
        };
        paymentFlowPort.concluded(concluded);
        return concluded;
    }

    private Payment claim(NewPayment requested) {
        Optional<Reason> refusal = refusal(requested);
        if (refusal.isPresent()) {
            paymentFlowPort.refused(requested, refusal.get());
            throw new PaymentRejectedException(refusal.get());
        }
        Payment claimed = paymentPort.claim(requested);
        if (claimed.sameRequestAs(requested)) {
            paymentFlowPort.requested(claimed);
        }
        return claimed;
    }

    private Optional<Reason> refusal(NewPayment requested) {
        if (!getMembershipPort.getMembership(requested.requestMembershipId()).valid()) {
            return Optional.of(Reason.INVALID_PAYER);
        }
        MembershipStatus merchant = getMembershipPort.getMembership(requested.franchiseId());
        if (!merchant.valid() || !merchant.corp()) {
            return Optional.of(Reason.INVALID_MERCHANT);
        }
        return Optional.empty();
    }

    @Override
    public List<Payment> getPaidPayments() {
        return paymentPort.findPaid();
    }

    @Override
    public SettlementOutcome finishSettlement(long paymentId) {
        boolean settledNow = paymentPort.settle(paymentId);
        Payment payment = paymentPort.find(paymentId).orElseThrow(() -> new PaymentRejectedException(Reason.PAYMENT_NOT_FOUND));
        if (payment.paymentStatus() != PaymentStatus.SETTLED) {
            throw new PaymentRejectedException(Reason.PAYMENT_NOT_PAID);
        }
        return new SettlementOutcome(payment, !settledNow);
    }
}
