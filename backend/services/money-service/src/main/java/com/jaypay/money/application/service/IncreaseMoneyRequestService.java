package com.jaypay.money.application.service;

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
import com.jaypay.money.application.port.out.MembershipStatus;
import com.jaypay.money.application.port.out.SendRechargingMoneyTaskPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
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
        /*
          Service logic detail - 페이 머니를 충전한다.
          1. 고객 정보 확인 (멤버)
          2. 연동된 계좌 유효성, 잔액 여부 체크 (뱅킹)
          3. 법인 계좌 상태 (뱅킹)
          4. 증액을 위한 기록, 요청 상태로 MoneyChangingRequest 생성 (MoneyChangingRequest)
          5. 펌뱅킹 수행 (고객 계좌 -> 제이페이 법인 계좌) (뱅킹)
          6-1. 결과가 정상이면 MoneyChangingRequest 상태값 변동 후 리턴
          6-2. 결과가 실패라면 MoneyChangingRequest 상태값 변동 후 리턴
         */

        // 1. Check the customer information
        MembershipStatus membershipStatus = getMembershipPort.getMembership(command.getTargetMembershipId());
        if (!membershipStatus.isValid()) {
            return null;
        }

        // 6-1 성공시 멤버의 MemberMoney 값 증액 필요
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

        // 6-2. 결과가 실패라면 MoneyChangingRequest 상태값 변동 후 리턴

        return null;
    }

    @Override
    public MoneyChangingRequest increaseMoneyRequestAsync(IncreaseMoneyRequestCommand command) {
        /*
         * 1. Subtask, Task
         * 2. Kafka cluster produce
         * 3. Wait
         * 3-1. Task-consumer
         *      Process sub-task and result ok -> task result produce
         * 4. Task Result Consume
         * 5. Consume ok, Logic
         */

        // Subtask
        // 각 서비스별 특정 membershipId로 validation 하기 위한 태스크로 정의

        // 1. Subtask, Task
        SubTask validMemberTask = SubTask.builder()
                .subTaskName("validMemberTask : " + "Validation of the membership")
                .membershipID(command.getTargetMembershipId())
                .taskType("membership")
                .status("ready")
                .build();

        // Banking Sub task
        // Banking Account Validation
        SubTask validBankingAccountTask = SubTask.builder()
                .subTaskName("validBankingAccountTask : " + "check validation of account")
                .membershipID(command.getTargetMembershipId())
                .taskType("banking")
                .status("ready")
                .build();

        // Amount Money Firmbanking -> Ok (가정)

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

        // 2. Kafka Cluster Produce
        // Task Produce
        sendRechargingMoneyTaskPort.sendRechargingMoneyTaskPort(task);
        countDownLatchManager.addCountDownLatch(task.getTaskID());

        // 3. Wait
        try {
            countDownLatchManager.getCountDownLatch(task.getTaskID()).await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 3-1. task-consumer
        // 등록된 sub-task, status ok -> task result Produce

        // 4. Task Result Consume
        // 받은 응답을 다시 countDownLatchManager 를 통해서 결과 데이터 받음
        String result = countDownLatchManager.getDataForKey(task.getTaskID());
        if (result.equals("success")) {
            // 4-1. Consume ok case
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
            // 4-2. Consume fail case
            return null;
        }

        // 5. Consume ok, Logic
        return null;
    }

    @Override
    public void createMemberMoney(CreateMemberMoneyCommand command) {
        MemberMoneyCreatedCommand axonCommand = new MemberMoneyCreatedCommand(command.getMembershipId());
        commandGateway.send(axonCommand).whenComplete((result, throwable) -> {
            if (throwable != null) {
                System.out.println("throwable: " + throwable);
                throw new RuntimeException(throwable);
            }
            System.out.println("result: " + result);
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

        // The command for starting SAGA process
        // RechargingMoneyRequestCreateCommand
        commandGateway.send(new RechargingMoneyRequestCreateCommand(
                memberMoneyAggregateIdentifier,
                UUID.randomUUID().toString(),
                command.getTargetMembershipId(),
                command.getAmount())
        ).whenComplete(
                (result, throwable) -> {
                    if (throwable != null) {
                        System.out.println(throwable.getMessage());
                    } else {
                        System.out.println("result: " + result);
                    }
                }
        );

//        String aggregateIdentifier = memberMoneyJpaEntity.getAggregateIdentifier();
//
//        commandGateway.send(IncreaseMemberMoneyCommand.builder()
//                        .aggregateIdentifier(aggregateIdentifier)
//                        .membershipId(command.getTargetMembershipId())
//                        .amount(command.getAmount()).build())
//                .whenComplete(
//                        (result, throwable) -> {
//                            if (throwable != null) {
//                                System.out.println("throwable: " + throwable);
//                                throw new RuntimeException(throwable);
//                            } else {
//                                // Increase money -> money increase
//                                System.out.println("increaseMoney result: " + result);
//
//                                increaseMoneyPort.increaseMoney(
//                                        new MemberMoney.MembershipId(command.getTargetMembershipId()),
//                                        command.getAmount()
//                                );
//                            }
//                        }
//                );
    }

}
