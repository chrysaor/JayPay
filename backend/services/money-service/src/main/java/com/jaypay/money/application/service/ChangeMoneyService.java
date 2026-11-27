package com.jaypay.money.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.UseCase;
import com.jaypay.money.application.port.in.ChangeMoneyCommand;
import com.jaypay.money.application.port.in.ChangeMoneyUseCase;
import com.jaypay.money.application.port.in.CreateMemberMoneyCommand;
import com.jaypay.money.application.port.in.CreateMemberMoneyUseCase;
import com.jaypay.money.application.port.out.ChangeMoneyPort;
import com.jaypay.money.application.port.out.CreateMemberMoneyAggregatePort;
import com.jaypay.money.application.port.out.CreateMemberMoneyPort;
import com.jaypay.money.application.port.out.GetMemberMoneyPort;
import com.jaypay.money.application.port.out.GetMembershipPort;
import com.jaypay.money.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.money.application.port.out.RechargeFlowPort;
import com.jaypay.money.application.port.out.RegisteredBankAccount;
import com.jaypay.money.application.port.out.RequestMoneyRechargingPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingStatus;
import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingType;
import com.jaypay.money.domain.RechargeFailure;
import com.jaypay.money.domain.RequestId;
import com.jaypay.money.domain.RequestIdReusedException;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChangeMoneyService implements ChangeMoneyUseCase, CreateMemberMoneyUseCase {

    private final GetMembershipPort getMembershipPort;
    private final ChangeMoneyPort changeMoneyPort;
    private final CreateMemberMoneyPort createMemberMoneyPort;
    private final GetMemberMoneyPort getMemberMoneyPort;
    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;
    private final CreateMemberMoneyAggregatePort createMemberMoneyAggregatePort;
    private final RequestMoneyRechargingPort requestMoneyRechargingPort;
    private final RechargeFlowPort rechargeFlowPort;

    @Override
    public MoneyChangingRequest increaseMoney(ChangeMoneyCommand command) {
        return change(command, MoneyChangingType.INCREASE);
    }

    @Override
    public MoneyChangingRequest decreaseMoney(ChangeMoneyCommand command) {
        return change(command, MoneyChangingType.DECREASE);
    }

    @Override
    public void createMemberMoney(CreateMemberMoneyCommand command) {
        walletOf(new MemberMoney.MembershipId(command.membershipId()));
    }

    @Override
    public MoneyChangingRequest increaseMoneyRequestByEvent(ChangeMoneyCommand command) {
        MoneyChangingRequest pending = pending(command, MoneyChangingType.INCREASE);
        Optional<MoneyChangingRequest> earlier = findEarlier(pending);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        Optional<RegisteredBankAccount> account = getRegisteredBankAccountPort.getRegisteredBankAccount(command.targetMembershipId())
                .filter(registered -> registered.bankAccountNumber() != null && !registered.bankAccountNumber().isBlank());
        if (account.isEmpty()) {
            log.info("Member {} has no registered bank account, so recharge {} fails", command.targetMembershipId(), command.requestId().value());
            rechargeFlowPort.requestFailed(pending, RechargeFailure.NO_BANK_ACCOUNT);
            return changeMoneyPort.settle(pending.settled(false));
        }
        rechargeFlowPort.requested(pending);
        MemberMoney.MembershipId membershipId = new MemberMoney.MembershipId(command.targetMembershipId());
        requestMoneyRechargingPort.requestMoneyRecharging(command.requestId(), walletOf(membershipId), membershipId, command.amount(), account.get());
        return pending;
    }

    @Override
    public boolean failRecharging(RequestId requestId, RechargeFailure failure) {
        return changeMoneyPort.findForUpdate(requestId).map(request -> {
            if (request.status() != MoneyChangingStatus.PENDING) {
                return request.status() == MoneyChangingStatus.FAILED;
            }
            changeMoneyPort.settle(request.settled(false));
            rechargeFlowPort.failed(request, failure);
            return true;
        }).orElse(true);
    }

    private MemberMoney.MoneyAggregateIdentifier walletOf(MemberMoney.MembershipId membershipId) {
        MemberMoney.MoneyAggregateIdentifier existing = getMemberMoneyPort.getMoneyAggregateIdentifier(membershipId);
        if (!existing.getAggregateIdentifier().isBlank()) {
            return existing;
        }
        createMemberMoneyPort.createMemberMoney(membershipId, createMemberMoneyAggregatePort.createMemberMoneyAggregate(membershipId));
        return getMemberMoneyPort.getMoneyAggregateIdentifier(membershipId);
    }

    @Override
    public boolean finishRecharging(RequestId requestId) {
        return changeMoneyPort.findForUpdate(requestId).map(request -> {
            if (request.status() != MoneyChangingStatus.PENDING) {
                return request.status() == MoneyChangingStatus.SUCCEEDED;
            }
            changeMoneyPort.increaseBalance(new MemberMoney.MembershipId(request.targetMembershipId()), request.amount());
            changeMoneyPort.settle(request.settled(true));
            rechargeFlowPort.walletIncreased(request);
            return true;
        }).orElse(false);
    }

    private MoneyChangingRequest change(ChangeMoneyCommand command, MoneyChangingType type) {
        MoneyChangingRequest pending = pending(command, type);
        Optional<MoneyChangingRequest> earlier = findEarlier(pending);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        boolean applied = getMembershipPort.getMembership(command.targetMembershipId()).isValid() && apply(pending);
        return changeMoneyPort.settle(pending.settled(applied));
    }

    private boolean apply(MoneyChangingRequest request) {
        MemberMoney.MembershipId membershipId = new MemberMoney.MembershipId(request.targetMembershipId());
        if (request.type() == MoneyChangingType.DECREASE) {
            return changeMoneyPort.decreaseBalance(membershipId, request.amount());
        }
        changeMoneyPort.increaseBalance(membershipId, request.amount());
        return true;
    }

    private Optional<MoneyChangingRequest> findEarlier(MoneyChangingRequest pending) {
        Optional<MoneyChangingRequest> earlier = changeMoneyPort.findEarlierOrClaim(pending);
        if (earlier.isPresent() && !earlier.get().sameChangeAs(pending)) {
            throw new RequestIdReusedException(pending.requestId());
        }
        return earlier;
    }

    private static MoneyChangingRequest pending(ChangeMoneyCommand command, MoneyChangingType type) {
        return MoneyChangingRequest.pending(command.requestId(), command.targetMembershipId(), type, command.amount());
    }

}
