package com.jaypay.payment.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.payment.application.port.in.FinishSettlementCommand;
import com.jaypay.payment.application.port.in.FinishSettlementUseCase;
import com.jaypay.payment.application.port.in.RequestPaymentCommand;
import com.jaypay.payment.application.port.in.RequestPaymentUseCase;
import com.jaypay.payment.application.port.out.*;
import com.jaypay.payment.domain.Payment;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;
import java.util.List;

@UseCase
@RequiredArgsConstructor
@Transactional
public class PaymentService implements RequestPaymentUseCase, FinishSettlementUseCase {

    private final CreatePaymentPort createPaymentPort;
    private final GetMembershipPort getMembershipPort;
    private final FinishSettlementPort finishSettlementPort;
    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;

    // Todo Money Service -> Member Money 정보를 가져오기 위한 Port

    @Override
    public Payment requestPayment(RequestPaymentCommand command) {
        // Check membership status
        MembershipStatus membershipStatus = getMembershipPort.getMembership(command.getRequestMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        // Check bank account
        //getRegisteredBankAccountPort.getRegisteredBankAccount(command.getRequestMembershipId());

        // Create payment
        return createPaymentPort.createPayment(
                command.getRequestMembershipId(),
                command.getRequestPrice(),
                command.getFranchiseId(),
                command.getFranchiseFeeRate());
    }

    @Override
    public List<Payment> getNormalStatusPayments() {
        return finishSettlementPort.getNormalStatusPayments();
    }

    @Override
    public void finishPayment(FinishSettlementCommand command) {
        finishSettlementPort.changePaymentRequestStatus(command.getPaymentId(), 2);
    }

}
