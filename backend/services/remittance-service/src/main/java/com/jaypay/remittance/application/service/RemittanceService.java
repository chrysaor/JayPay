package com.jaypay.remittance.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.remittance.application.port.in.FindRemittanceUseCase;
import com.jaypay.remittance.application.port.in.RequestRemittanceUseCase;
import com.jaypay.remittance.application.port.in.ResumeUnfinishedRemittancesUseCase;
import com.jaypay.remittance.application.port.out.BankingPort;
import com.jaypay.remittance.application.port.out.MembershipPort;
import com.jaypay.remittance.application.port.out.RemittanceFlowPort;
import com.jaypay.remittance.application.port.out.RemittancePort;
import com.jaypay.remittance.application.port.out.RemittancePort.SweptRemittance;
import com.jaypay.remittance.application.port.out.WalletPort;
import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Receiver;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceRejectedException;
import com.jaypay.remittance.domain.RemittanceRejectedException.Reason;
import com.jaypay.remittance.domain.RemittanceStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@UseCase
@Slf4j
public class RemittanceService implements RequestRemittanceUseCase, FindRemittanceUseCase, ResumeUnfinishedRemittancesUseCase {

    private final RemittancePort remittancePort;
    private final MembershipPort membershipPort;
    private final WalletPort walletPort;
    private final BankingPort bankingPort;
    private final RemittanceFlowPort remittanceFlowPort;
    private final Duration sweepRetryAfter;
    private final int sweepAttempts;

    public RemittanceService(RemittancePort remittancePort,
                             MembershipPort membershipPort,
                             WalletPort walletPort,
                             BankingPort bankingPort,
                             RemittanceFlowPort remittanceFlowPort,
                             @Value("${remittance.sweep.retry-after}") Duration sweepRetryAfter,
                             @Value("${remittance.sweep.attempts}") int sweepAttempts) {
        this.remittancePort = remittancePort;
        this.membershipPort = membershipPort;
        this.walletPort = walletPort;
        this.bankingPort = bankingPort;
        this.remittanceFlowPort = remittanceFlowPort;
        this.sweepRetryAfter = sweepRetryAfter;
        this.sweepAttempts = sweepAttempts;
    }

    @Override
    public Remittance requestRemittance(NewRemittance requested) {
        Remittance remittance = remittancePort.findByRequestId(requested.requestId()).orElseGet(() -> claim(requested));
        if (!remittance.sameRequestAs(requested)) {
            throw new RemittanceRejectedException(Reason.REQUEST_ID_REUSED);
        }
        Remittance reached = drive(remittance);
        if (!reached.status().finished()) {
            throw new RemittanceRejectedException(Reason.REMITTANCE_NOT_CONFIRMED);
        }
        return reached;
    }

    @Override
    public void resumeUnfinishedRemittances() {
        for (SweptRemittance swept : remittancePort.sweep(sweepRetryAfter, sweepAttempts)) {
            Remittance reached = drive(swept.remittance());
            if (reached.status().finished()) {
                log.info("The sweep finished remittance {} as {}", reached.remittanceId(), reached.status());
            } else if (swept.attempt() >= sweepAttempts) {
                log.error("Remittance {} stays {}: the call that moves it on was not confirmed after {} sweeps",
                        reached.remittanceId(), reached.status(), swept.attempt());
            }
        }
    }

    @Override
    public List<Remittance> findRemittanceHistory(String membershipId) {
        return remittancePort.findSentOrReceivedBy(membershipId);
    }

    private Remittance claim(NewRemittance requested) {
        Optional<Reason> refusal = refusal(requested);
        if (refusal.isPresent()) {
            remittanceFlowPort.refused(requested, refusal.get());
            throw new RemittanceRejectedException(refusal.get());
        }
        Remittance claimed = remittancePort.claim(requested);
        if (claimed.sameRequestAs(requested)) {
            remittanceFlowPort.requested(claimed);
        }
        return claimed;
    }

    private Optional<Reason> refusal(NewRemittance requested) {
        if (!membershipPort.isValid(requested.fromMembershipId())) {
            return Optional.of(Reason.INVALID_SENDER);
        }
        return switch (requested.receiver()) {
            case Receiver.Member(String membershipId) when !membershipPort.isValid(membershipId) -> Optional.of(Reason.INVALID_RECEIVER);
            case Receiver.BankAccount account when !bankingPort.isValid(account) -> Optional.of(Reason.INVALID_BANK_ACCOUNT);
            default -> Optional.empty();
        };
    }

    private Remittance drive(Remittance remittance) {
        Remittance current = remittance;
        while (true) {
            Optional<Remittance> next = switch (current.status()) {
                case COMPLETED, FAILED, REFUNDED -> Optional.empty();
                case REQUESTED -> debit(current);
                case DEBITED -> deliver(current);
                case TRANSFERRING -> transfer(current);
                case REFUNDING -> refund(current);
            };
            if (next.isEmpty()) {
                return current;
            }
            current = next.get();
        }
    }

    private Optional<Remittance> debit(Remittance remittance) {
        NewRemittance request = remittance.request();
        return switch (walletPort.decrease(remittance.debitRequestId(), request.fromMembershipId(), request.amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.DEBITED);
            case FAILED -> move(remittance, RemittanceStatus.FAILED);
            case UNCONFIRMED -> Optional.empty();
        };
    }

    private Optional<Remittance> deliver(Remittance remittance) {
        return switch (remittance.request().receiver()) {
            case Receiver.Member member -> credit(remittance, member);
            case Receiver.BankAccount account -> move(remittance, RemittanceStatus.TRANSFERRING);
        };
    }

    private Optional<Remittance> credit(Remittance remittance, Receiver.Member member) {
        return switch (walletPort.increase(remittance.creditRequestId(), member.membershipId(), remittance.request().amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.COMPLETED);
            case FAILED -> move(remittance, RemittanceStatus.REFUNDING);
            case UNCONFIRMED -> Optional.empty();
        };
    }

    private Optional<Remittance> transfer(Remittance remittance) {
        Receiver.BankAccount account = (Receiver.BankAccount) remittance.request().receiver();
        return switch (bankingPort.transfer(remittance.transferRequestId(), account, remittance.request().amount())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.COMPLETED);
            case FAILED -> move(remittance, RemittanceStatus.REFUNDING);
            case UNCONFIRMED -> Optional.empty();
        };
    }

    private Optional<Remittance> refund(Remittance remittance) {
        return switch (walletPort.refund(remittance.refundRequestId(), remittance.debitRequestId())) {
            case SUCCEEDED -> move(remittance, RemittanceStatus.REFUNDED);
            case FAILED, UNCONFIRMED -> Optional.empty();
        };
    }

    private Optional<Remittance> move(Remittance remittance, RemittanceStatus next) {
        if (remittancePort.advance(remittance.remittanceId(), remittance.status(), next)) {
            remittanceFlowPort.advanced(remittance, next);
        }
        return Optional.of(remittancePort.find(remittance.remittanceId()));
    }
}
