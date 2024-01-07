package com.jaypay.money.application.service;

import com.jaypay.common.UseCase;
import com.jaypay.money.adapter.out.persistence.MemberMoneyJpaEntity;
import com.jaypay.money.adapter.out.persistence.MoneyChangingRequestMapper;
import com.jaypay.money.application.port.in.IncreaseMoneyRequestCommand;
import com.jaypay.money.application.port.in.IncreaseMoneyRequestUseCase;
import com.jaypay.money.application.port.out.IncreaseMoneyPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;

import javax.transaction.Transactional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
@Transactional
public class IncreaseMoneyRequestService implements IncreaseMoneyRequestUseCase {

    private final IncreaseMoneyPort increaseMoneyPort;
    private final MoneyChangingRequestMapper mapper;

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
}