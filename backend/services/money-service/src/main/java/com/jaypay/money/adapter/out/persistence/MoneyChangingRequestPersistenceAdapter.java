package com.jaypay.money.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.money.application.port.out.CreateMemberMoneyPort;
import com.jaypay.money.application.port.out.GetMemberMoneyListPort;
import com.jaypay.money.application.port.out.GetMemberMoneyPort;
import com.jaypay.money.application.port.out.IncreaseMoneyPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyChangingRequest;
import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;


@PersistenceAdapter
@RequiredArgsConstructor
public class MoneyChangingRequestPersistenceAdapter implements IncreaseMoneyPort, CreateMemberMoneyPort, GetMemberMoneyPort, GetMemberMoneyListPort {

    private final SpringDataMoneyChangingRequestRepository moneyChangingRequestRepository;
    private final SpringDataMemberMoneyRepository memberMoneyRepository;

    @Override
    public MoneyChangingRequest createMoneyChangingRequest(MoneyChangingRequest.TargetMembershipId targetMembershipId, MoneyChangingRequest.MoneyChangingType moneyChangingType, MoneyChangingRequest.ChangingMoneyAmount changingMoneyAmount, MoneyChangingRequest.MoneyChangingStatus moneyChangingStatus, MoneyChangingRequest.Uuid uuid) {
        return toDomain(moneyChangingRequestRepository.save(
                new MoneyChangingRequestJpaEntity(
                        targetMembershipId.getTargetMembershipId(),
                        moneyChangingType.getMoneyChangingType(),
                        changingMoneyAmount.getChangingMoneyAmount(),
                        new Timestamp(System.currentTimeMillis()),
                        moneyChangingStatus.getChangingMoneyStatus(),
                        UUID.randomUUID()
                )
        ));
    }

    @Override
    public MemberMoney increaseMoney(MemberMoney.MembershipId membershipId, int increaseMoneyAmount) {
        MemberMoneyJpaEntity entity;
        try {
            List<MemberMoneyJpaEntity> entityList = memberMoneyRepository.findByMembershipId(Long.parseLong(membershipId.getMembershipId()));
            entity = entityList.get(0);

            entity.setBalance(entity.getBalance() + increaseMoneyAmount);
            return toDomain(memberMoneyRepository.save(entity));
        } catch (Exception e) {
            entity = new MemberMoneyJpaEntity(
                    Long.parseLong(membershipId.getMembershipId()),
                    increaseMoneyAmount,
                    ""
            );
            return toDomain(memberMoneyRepository.save(entity));
        }
    }

    @Override
    public void createMemberMoney(MemberMoney.MembershipId membershipId, MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier) {
        MemberMoneyJpaEntity entity = new MemberMoneyJpaEntity(
                Long.parseLong(membershipId.getMembershipId()),
                0,
                moneyAggregateIdentifier.getAggregateIdentifier()
        );
        memberMoneyRepository.save(entity);
    }

    @Override
    public MemberMoney.MoneyAggregateIdentifier getMoneyAggregateIdentifier(MemberMoney.MembershipId membershipId) {
        List<MemberMoneyJpaEntity> entityList = memberMoneyRepository.findByMembershipId(Long.parseLong(membershipId.getMembershipId()));

        MemberMoneyJpaEntity entity = entityList.isEmpty()
                ? memberMoneyRepository.save(new MemberMoneyJpaEntity(Long.parseLong(membershipId.getMembershipId()), 0, ""))
                : entityList.get(0);

        return new MemberMoney.MoneyAggregateIdentifier(entity.getAggregateIdentifier());
    }

    @Override
    public List<MemberMoney> getMemberMoneys(List<String> membershipIds) {
        List<Long> longList = membershipIds.stream().map(Long::parseLong).toList();

        return memberMoneyRepository.fineMemberMoneyListByMembershipIds(longList).stream()
                .map(MoneyChangingRequestPersistenceAdapter::toDomain)
                .toList();
    }

    private static MemberMoney toDomain(MemberMoneyJpaEntity entity) {
        return MemberMoney.generateMemberMoney(
                new MemberMoney.MemberMoneyId(entity.getMemberMoneyId() + ""),
                new MemberMoney.MembershipId(entity.getMembershipId() + ""),
                new MemberMoney.Balance(entity.getBalance())
        );
    }

    private static MoneyChangingRequest toDomain(MoneyChangingRequestJpaEntity entity) {
        return MoneyChangingRequest.generateMoneyChangingRequest(
                new MoneyChangingRequest.MoneyChangingRequestId(entity.getMoneyChangingRequestId() + ""),
                new MoneyChangingRequest.TargetMembershipId(entity.getTargetMembershipId()),
                new MoneyChangingRequest.MoneyChangingType(entity.getMoneyChangingType()),
                new MoneyChangingRequest.ChangingMoneyAmount(entity.getMoneyAmount()),
                new MoneyChangingRequest.MoneyChangingStatus(entity.getChangingMoneyStatus()),
                entity.getUuid()
        );
    }

}
