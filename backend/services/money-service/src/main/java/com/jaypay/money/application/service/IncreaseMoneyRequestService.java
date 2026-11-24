package com.jaypay.money.application.service;

import lombok.extern.slf4j.Slf4j;
import com.jaypay.common.CountDownLatchManager;
import com.jaypay.common.RechargingMoneyTask;
import com.jaypay.common.SubTask;
import com.jaypay.common.UseCase;
import com.jaypay.money.adapter.axon.command.MemberMoneyCreatedCommand;
import com.jaypay.money.adapter.axon.command.RechargingMoneyRequestCreateCommand;
import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;
import com.jaypay.money.adapter.out.persistence.MoneyChangingRequestMapper;
import com.jaypay.money.application.port.in.*;
import com.jaypay.money.application.port.out.GetMembershipPort;
import com.jaypay.money.application.port.out.IncreaseMoneyPort;
import com.jaypay.money.application.port.out.SendRechargingMoneyTaskPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;

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
    private final MoneyChangingRequestMapper mapper;
    private final CommandGateway commandGateway;

    @Override
    public MoneyChangingRequest increaseMoneyRequest(IncreaseMoneyRequestCommand command) {
        boolean validMember = getMembershipPort.getMembership(command.getTargetMembershipId()).isValid();
        if (validMember) {
            increaseMoneyPort.increaseMoney(
                    new MemberMoney.MembershipId(command.getTargetMembershipId()),
                    command.getAmount()
            );
        }

        return mapper.mapToDomainEntity(increaseMoneyPort.createMoneyChangingRequest(
                new MoneyChangingRequest.TargetMembershipId(command.getTargetMembershipId()),
                new MoneyChangingRequest.MoneyChangingType(1),
                new MoneyChangingRequest.ChangingMoneyAmount(command.getAmount()),
                validMember ? MoneyChangingRequest.MoneyChangingStatus.SUCCEEDED : MoneyChangingRequest.MoneyChangingStatus.FAILED,
                new MoneyChangingRequest.Uuid(UUID.randomUUID().toString()))
        );
    }

    @Override
    public MoneyChangingRequest increaseMoneyRequestAsync(IncreaseMoneyRequestCommand command) {
        SubTask validMemberTask = SubTask.builder()
                .subTaskName("validMemberTask : " + "Validation of the membership")
                .membershipID(command.getTargetMembershipId())
                .taskType("membership")
                .status("ready")
                .build();

        SubTask validBankingAccountTask = SubTask.builder()
                .subTaskName("validBankingAccountTask : " + "check validation of account")
                .membershipID(command.getTargetMembershipId())
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
                .moneyAmount(command.getAmount())
                .membershipID(command.getTargetMembershipId())
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
            MemberMoneyJpaEntity memberMoneyJpaEntity = increaseMoneyPort.increaseMoney(
                    new MemberMoney.MembershipId(command.getTargetMembershipId()),
                    command.getAmount()
            );

            if (memberMoneyJpaEntity != null) {
                return mapper.mapToDomainEntity(increaseMoneyPort.createMoneyChangingRequest(
                        new MoneyChangingRequest.TargetMembershipId(command.getTargetMembershipId()),
                        new MoneyChangingRequest.MoneyChangingType(1),
                        new MoneyChangingRequest.ChangingMoneyAmount(command.getAmount()),
                        new MoneyChangingRequest.MoneyChangingStatus(1),
                        new MoneyChangingRequest.Uuid(UUID.randomUUID().toString()))
                );
            }
        } else {
            return null;
        }

        return null;
    }

    @Override
    public void createMemberMoney(CreateMemberMoneyCommand command) {
        MemberMoneyCreatedCommand axonCommand = new MemberMoneyCreatedCommand(command.getMembershipId());
        commandGateway.send(axonCommand).whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.warn("throwable: {}", throwable);
                throw new RuntimeException(throwable);
            }
            log.info("result: {}", result);
            createMemberMoneyPort.createMemberMoney(
                    new MemberMoney.MembershipId(command.getMembershipId()),
                    new MemberMoney.MoneyAggregateIdentifier(result.toString())
            );
        });
    }

    @Override
    public void increaseMoneyRequestByEvent(IncreaseMoneyRequestCommand command) {
        MemberMoneyJpaEntity memberMoneyJpaEntity = getMemberMoneyPort.getMemberMoney(
                new MemberMoney.MembershipId(command.getTargetMembershipId())
        );

        String memberMoneyAggregateIdentifier = memberMoneyJpaEntity.getAggregateIdentifier();

        commandGateway.send(new RechargingMoneyRequestCreateCommand(
                memberMoneyAggregateIdentifier,
                UUID.randomUUID().toString(),
                command.getTargetMembershipId(),
                command.getAmount())
        ).whenComplete(
                (result, throwable) -> {
                    if (throwable != null) {
                        log.warn("{}", throwable.getMessage());
                    } else {
                        log.info("result: {}", result);
                    }
                }
        );
    }

}
