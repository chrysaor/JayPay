package com.jaypay.remittance.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.remittance.application.port.in.FindRemittanceUseCase;
import com.jaypay.remittance.application.port.in.RequestRemittanceUseCase;
import com.jaypay.remittance.application.port.out.BankingPort;
import com.jaypay.remittance.application.port.out.MembershipPort;
import com.jaypay.remittance.application.port.out.RemittancePort;
import com.jaypay.remittance.application.port.out.WalletPort;
import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Receiver;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceRejectedException;
import com.jaypay.remittance.domain.RemittanceRejectedException.Reason;
import com.jaypay.remittance.domain.RemittanceStatus;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class RemittanceService implements RequestRemittanceUseCase, FindRemittanceUseCase {

    private final RemittancePort remittancePort;
    private final MembershipPort membershipPort;
    private final WalletPort walletPort;
    private final BankingPort bankingPort;

    @Override
    public Remittance requestRemittance(NewRemittance requested) {
        Remittance remittance = remittancePort.findByRequestId(requested.requestId()).orElseGet(() -> {
            checkParties(requested);
            return remittancePort.claim(requested);
        });
        if (!remittance.sameRequestAs(requested)) {
            throw new RemittanceRejectedException(Reason.REQUEST_ID_REUSED);
        }
        return drive(remittance);
    }

    @Override
    public List<Remittance> findRemittanceHistory(String membershipId) {
        return remittancePort.findSentOrReceivedBy(membershipId);
    }

    private void checkParties(NewRemittance requested) {
        if (!membershipPort.isValid(requested.fromMembershipId())) {
            throw new RemittanceRejectedException(Reason.INVALID_SENDER);
        }
        switch (requested.receiver()) {
            case Receiver.Member(String membershipId) when !membershipPort.isValid(membershipId) ->
                    throw new RemittanceRejectedException(Reason.INVALID_RECEIVER);
            case Receiver.BankAccount account when !bankingPort.isValid(account) ->
                    throw new RemittanceRejectedException(Reason.INVALID_BANK_ACCOUNT);
            default -> {
            }
        }
    }

    private Remittance drive(Remittance remittance) {
        Remittance current = remittance;
        while (true) {
            switch (current.status()) {
                case COMPLETED, FAILED, REFUNDED -> {
                    return current;
                }
                case REQUESTED -> current = debit(current);
                case DEBITED -> current = deliver(current);
                case TRANSFERRING -> current = transfer(current);
                case REFUNDING -> current = refund(current);
            }
        }
    }

    private Remittance debit(Remittance remittance) {
        NewRemittance request = remittance.request();
        return switch (walletPort.decrease(remittance.debitRequestId(), request.fromMembershipId(), request.amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.DEBITED);
            case FAILED -> move(remittance, RemittanceStatus.FAILED);
            case UNCONFIRMED -> throw new RemittanceRejectedException(Reason.REMITTANCE_NOT_CONFIRMED);
        };
    }

    private Remittance deliver(Remittance remittance) {
        return switch (remittance.request().receiver()) {
            case Receiver.Member member -> credit(remittance, member);
            case Receiver.BankAccount account -> startTransfer(remittance);
        };
    }

    private Remittance credit(Remittance remittance, Receiver.Member member) {
        return switch (walletPort.increase(remittance.creditRequestId(), member.membershipId(), remittance.request().amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.COMPLETED);
            case FAILED -> move(remittance, RemittanceStatus.REFUNDING);
            case UNCONFIRMED -> throw new RemittanceRejectedException(Reason.REMITTANCE_NOT_CONFIRMED);
        };
    }

    private Remittance startTransfer(Remittance remittance) {
        remittancePort.advance(remittance.remittanceId(), RemittanceStatus.DEBITED, RemittanceStatus.TRANSFERRING);
        return remittancePort.find(remittance.remittanceId());
    }

    private Remittance transfer(Remittance remittance) {
        Receiver.BankAccount account = (Receiver.BankAccount) remittance.request().receiver();
        return switch (bankingPort.transfer(remittance.transferRequestId(), account, remittance.request().amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.COMPLETED);
            case FAILED -> move(remittance, RemittanceStatus.REFUNDING);
            case UNCONFIRMED -> throw new RemittanceRejectedException(Reason.REMITTANCE_NOT_CONFIRMED);
        };
    }

    private Remittance refund(Remittance remittance) {
        NewRemittance request = remittance.request();
        return switch (walletPort.increase(remittance.refundRequestId(), request.fromMembershipId(), request.amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.REFUNDED);
            case FAILED, UNCONFIRMED -> throw new RemittanceRejectedException(Reason.REMITTANCE_NOT_CONFIRMED);
        };
    }

    private Remittance move(Remittance remittance, RemittanceStatus next) {
        remittancePort.advance(remittance.remittanceId(), remittance.status(), next);
        return remittancePort.find(remittance.remittanceId());
    }
}
