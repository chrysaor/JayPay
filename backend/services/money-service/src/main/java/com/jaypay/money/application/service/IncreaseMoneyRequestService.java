package com.jaypay.money.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.CountDownLatchManager;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.common.SubTask;
import com.jaypay.common.UseCase;
import com.jaypay.money.application.port.in.CreateMemberMoneyCommand;
import com.jaypay.money.application.port.in.CreateMemberMoneyUseCase;
import com.jaypay.money.application.port.in.IncreaseMoneyRequestCommand;
import com.jaypay.money.application.port.in.IncreaseMoneyRequestUseCase;
import com.jaypay.money.application.port.out.CreateMemberMoneyAggregatePort;
import com.jaypay.money.application.port.out.CreateMemberMoneyPort;
import com.jaypay.money.application.port.out.GetMemberMoneyPort;
import com.jaypay.money.application.port.out.GetMembershipPort;
import com.jaypay.money.application.port.out.GetRegisteredBankAccountPort;
import com.jaypay.money.application.port.out.IncreaseMoneyPort;
import com.jaypay.money.application.port.out.RequestMoneyRechargingPort;
import com.jaypay.money.application.port.out.SendRechargingMoneyTaskPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
@Slf4j
public class IncreaseMoneyRequestService implements IncreaseMoneyRequestUseCase, CreateMemberMoneyUseCase {

    private final CountDownLatchManager countDownLatchManager;
    private final SendRechargingMoneyTaskPort sendRechargingMoneyTaskPort;
    private final GetMembershipPort getMembershipPort;
    private final IncreaseMoneyPort increaseMoneyPort;
    private final CreateMemberMoneyPort createMemberMoneyPort;
    private final GetMemberMoneyPort getMemberMoneyPort;
    private final GetRegisteredBankAccountPort getRegisteredBankAccountPort;
    private final CreateMemberMoneyAggregatePort createMemberMoneyAggregatePort;
    private final RequestMoneyRechargingPort requestMoneyRechargingPort;

    @Override
    public MoneyChangingRequest increaseMoneyRequest(IncreaseMoneyRequestCommand command) {
        boolean validMember = getMembershipPort.getMembership(command.targetMembershipId()).isValid();
        if (validMember) {
            increaseMoney(command);
        }

        return recordIncrease(command, validMember
                ? MoneyChangingRequest.MoneyChangingStatus.SUCCEEDED
                : MoneyChangingRequest.MoneyChangingStatus.FAILED);
    }

    @Override
    public MoneyChangingRequest increaseMoneyRequestAsync(IncreaseMoneyRequestCommand command) {
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
                .moneyAmount(command.amount())
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
            increaseMoney(command);
            return recordIncrease(command, MoneyChangingRequest.MoneyChangingStatus.SUCCEEDED);
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
    public void increaseMoneyRequestByEvent(IncreaseMoneyRequestCommand command) {
        MemberMoney.MembershipId membershipId = new MemberMoney.MembershipId(command.targetMembershipId());
        requestMoneyRechargingPort.requestMoneyRecharging(
                getMemberMoneyPort.getMoneyAggregateIdentifier(membershipId),
                membershipId,
                command.amount(),
                getRegisteredBankAccountPort.getRegisteredBankAccount(command.targetMembershipId())
        );
    }

    @Override
    public MemberMoney increaseMoney(IncreaseMoneyRequestCommand command) {
        return increaseMoneyPort.increaseMoney(new MemberMoney.MembershipId(command.targetMembershipId()), command.amount());
    }

    private MoneyChangingRequest recordIncrease(IncreaseMoneyRequestCommand command, MoneyChangingRequest.MoneyChangingStatus status) {
        return increaseMoneyPort.createMoneyChangingRequest(
                new MoneyChangingRequest.TargetMembershipId(command.targetMembershipId()),
                new MoneyChangingRequest.MoneyChangingType(1),
                new MoneyChangingRequest.ChangingMoneyAmount(command.amount()),
                status,
                new MoneyChangingRequest.Uuid(UUID.randomUUID().toString())
        );
    }

}
