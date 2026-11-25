package com.jaypay.money.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.CountDownLatchManager;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.common.SubTask;
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
import com.jaypay.money.application.port.out.RequestMoneyRechargingPort;
import com.jaypay.money.application.port.out.SendRechargingMoneyTaskPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingStatus;
import com.jaypay.money.domain.MoneyChangingRequest.MoneyChangingType;
import com.jaypay.money.domain.RequestId;
import com.jaypay.money.domain.RequestIdReusedException;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChangeMoneyService implements ChangeMoneyUseCase, CreateMemberMoneyUseCase {

    private final CountDownLatchManager countDownLatchManager;
    private final SendRechargingMoneyTaskPort sendRechargingMoneyTaskPort;
    private final GetMembershipPort getMembershipPort;
    private final ChangeMoneyPort changeMoneyPort;
    private final CreateMemberMoneyPort createMemberMoneyPort;
    private final GetMemberMoneyPort getMemberMoneyPort;
    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;
    private final CreateMemberMoneyAggregatePort createMemberMoneyAggregatePort;
    private final RequestMoneyRechargingPort requestMoneyRechargingPort;

    @Override
    public MoneyChangingRequest increaseMoney(ChangeMoneyCommand command) {
        return change(command, MoneyChangingType.INCREASE);
    }

    @Override
    public MoneyChangingRequest decreaseMoney(ChangeMoneyCommand command) {
        return change(command, MoneyChangingType.DECREASE);
    }

    @Override
    public MoneyChangingRequest increaseMoneyRequestAsync(ChangeMoneyCommand command) {
        SubTask validMemberTask = SubTask.builder()
                .subTaskName("validMemberTask : " + "Validation of the membership")
                .membershipID(command.targetMembershipId())
                .taskType("membership")
                .status("ready")
                .build();

        SubTask validBankingAccountTask = SubTask.builder()
                .subTaskName("validBankingAccountTask : " + "check validation of account")
                .membershipID(command.targetMembershipId())
                .taskType("banking")
                .status("ready")
                .build();

        List<SubTask> subTaskList = new ArrayList<>();
        subTaskList.add(validMemberTask);
        subTaskList.add(validBankingAccountTask);

        RechargingMoneyTask task = RechargingMoneyTask.builder()
                .taskID(UUID.randomUUID().toString())
                .taskName("Increase Money Task")
                .subTaskList(subTaskList)
                .moneyAmount(Math.toIntExact(command.amount().value()))
                .membershipID(command.targetMembershipId())
                .toBankName("jay")
                .build();

        sendRechargingMoneyTaskPort.sendRechargingMoneyTaskPort(task);
        countDownLatchManager.addCountDownLatch(task.getTaskID());

        try {
            countDownLatchManager.getCountDownLatch(task.getTaskID()).await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String result = countDownLatchManager.getDataForKey(task.getTaskID());
        if (result.equals("success")) {
            return increaseMoney(command);
        }

        return null;
    }

    @Override
    public void createMemberMoney(CreateMemberMoneyCommand command) {
        MemberMoney.MembershipId membershipId = new MemberMoney.MembershipId(command.membershipId());
        createMemberMoneyAggregatePort.createMemberMoneyAggregate(membershipId).whenComplete((aggregateIdentifier, throwable) -> {
            if (throwable != null) {
                log.warn("throwable: {}", throwable);
                return;
            }
            log.info("result: {}", aggregateIdentifier.getAggregateIdentifier());
            createMemberMoneyPort.createMemberMoney(membershipId, aggregateIdentifier);
        });
    }

    @Override
    public void increaseMoneyRequestByEvent(ChangeMoneyCommand command) {
        MoneyChangingRequest pending = pending(command, MoneyChangingType.INCREASE);
        if (findEarlier(pending).isPresent()) {
            return;
        }
        MemberMoney.MembershipId membershipId = new MemberMoney.MembershipId(command.targetMembershipId());
        requestMoneyRechargingPort.requestMoneyRecharging(
                command.requestId(),
                getMemberMoneyPort.getMoneyAggregateIdentifier(membershipId),
                membershipId,
                command.amount(),
                getRegisteredBankAccountPort.getRegisteredBankAccount(command.targetMembershipId())
        );
    }

    @Override
    public boolean finishRecharging(RequestId requestId) {
        return changeMoneyPort.findForUpdate(requestId).map(request -> {
            if (request.status() != MoneyChangingStatus.PENDING) {
                return request.status() == MoneyChangingStatus.SUCCEEDED;
            }
            changeMoneyPort.increaseBalance(new MemberMoney.MembershipId(request.targetMembershipId()), request.amount());
            changeMoneyPort.settle(request.settled(true));
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
