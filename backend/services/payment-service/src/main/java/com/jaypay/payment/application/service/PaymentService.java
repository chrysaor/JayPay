package com.jaypay.payment.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.payment.application.port.in.FinishSettlementUseCase;
import com.jaypay.payment.application.port.in.RequestPaymentCommand;
import com.jaypay.payment.application.port.in.RequestPaymentUseCase;
import com.jaypay.payment.application.port.in.SettlementOutcome;
import com.jaypay.payment.application.port.out.DecreaseMoneyPort;
import com.jaypay.payment.application.port.out.GetMembershipPort;
import com.jaypay.payment.application.port.out.MembershipStatus;
import com.jaypay.payment.application.port.out.PaymentPort;
import com.jaypay.payment.domain.FeeRate;
import com.jaypay.payment.domain.NewPayment;
import com.jaypay.payment.domain.Payment;
import com.jaypay.payment.domain.PaymentRejectedException;
import com.jaypay.payment.domain.PaymentRejectedException.Reason;
import com.jaypay.payment.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@UseCase
public class PaymentService implements RequestPaymentUseCase, FinishSettlementUseCase {

    private final PaymentPort paymentPort;
    private final GetMembershipPort getMembershipPort;
    private final DecreaseMoneyPort decreaseMoneyPort;
    private final FeeRate feeRate;

    public PaymentService(PaymentPort paymentPort,
                          GetMembershipPort getMembershipPort,
                          DecreaseMoneyPort decreaseMoneyPort,
                          @Value("${payment.fee-rate-basis-points}") int feeRateBasisPoints) {
        this.paymentPort = paymentPort;
        this.getMembershipPort = getMembershipPort;
        this.decreaseMoneyPort = decreaseMoneyPort;
        this.feeRate = new FeeRate(feeRateBasisPoints);
    }

    @Override
    public Payment requestPayment(RequestPaymentCommand command) {
        if (!getMembershipPort.getMembership(command.requestMembershipId()).valid()) {
            throw new PaymentRejectedException(Reason.INVALID_PAYER);
        }
        MembershipStatus merchant = getMembershipPort.getMembership(command.franchiseId());
        if (!merchant.valid() || !merchant.corp()) {
            throw new PaymentRejectedException(Reason.INVALID_MERCHANT);
        }

        NewPayment requested = NewPayment.of(command.requestId(), command.requestMembershipId(), command.franchiseId(),
                command.requestPrice(), feeRate);
        Payment payment = paymentPort.claim(requested);
        if (!payment.sameRequestAs(requested)) {
            throw new PaymentRejectedException(Reason.REQUEST_ID_REUSED);
        }
        if (payment.paymentStatus() != PaymentStatus.REQUESTED) {
            return payment;
        }

        return switch (decreaseMoneyPort.decreaseMoney(payment.debitRequestId(), payment.requestMembershipId(), payment.requestPrice())) {
            case SUCCEEDED -> paymentPort.conclude(payment.paymentId(), PaymentStatus.PAID);
            case FAILED -> paymentPort.conclude(payment.paymentId(), PaymentStatus.FAILED);
            case UNCONFIRMED -> throw new PaymentRejectedException(Reason.PAYMENT_NOT_CONFIRMED);
        };
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
