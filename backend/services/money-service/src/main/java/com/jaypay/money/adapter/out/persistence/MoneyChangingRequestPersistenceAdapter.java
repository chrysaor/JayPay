package com.jaypay.money.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.money.application.port.out.ChangeMoneyPort;
import com.jaypay.money.application.port.out.CreateMemberMoneyPort;
import com.jaypay.money.application.port.out.GetMemberMoneyListPort;
import com.jaypay.money.application.port.out.GetMemberMoneyPort;
import com.jaypay.money.domain.MemberMoney;
import com.jaypay.money.domain.MoneyAmount;
import com.jaypay.money.domain.MoneyChangingRequest;
import com.jaypay.money.domain.RequestId;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;


@PersistenceAdapter
@RequiredArgsConstructor
public class MoneyChangingRequestPersistenceAdapter implements ChangeMoneyPort, CreateMemberMoneyPort, GetMemberMoneyPort, GetMemberMoneyListPort {

    private final SpringDataMoneyChangingRequestRepository moneyChangingRequestRepository;
    private final SpringDataMemberMoneyRepository memberMoneyRepository;

    @Override
    public Optional<MoneyChangingRequest> findEarlierOrClaim(MoneyChangingRequest pending) {
        int claimed = moneyChangingRequestRepository.claim(
                pending.requestId().value(),
                Long.parseLong(pending.targetMembershipId()),
                pending.type().name(),
                pending.amount().value()
        );
        if (claimed == 1) {
            return Optional.empty();
        }
        return moneyChangingRequestRepository.findById(pending.requestId().value()).map(MoneyChangingRequestPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<MoneyChangingRequest> findForUpdate(RequestId requestId) {
        return moneyChangingRequestRepository.findForUpdate(requestId.value()).map(MoneyChangingRequestPersistenceAdapter::toDomain);
    }

    @Override
    public MoneyChangingRequest settle(MoneyChangingRequest settled) {
        MoneyChangingRequestJpaEntity entity = moneyChangingRequestRepository.findById(settled.requestId().value()).orElseThrow();
        entity.setStatus(settled.status());
        return toDomain(entity);
    }

    @Override
    public Optional<RequestId> findRefundOf(RequestId refundedRequestId) {
        return moneyChangingRequestRepository.findByRefundedRequestId(refundedRequestId.value())
                .map(refund -> new RequestId(refund.getRequestId()));
    }

    @Override
    public MoneyChangingRequest settleRefund(MoneyChangingRequest settled, RequestId refundedRequestId) {
        MoneyChangingRequestJpaEntity entity = moneyChangingRequestRepository.findById(settled.requestId().value()).orElseThrow();
        entity.setStatus(settled.status());
        entity.setRefundedRequestId(refundedRequestId.value());
        return toDomain(entity);
    }

    @Override
    public void increaseBalance(MemberMoney.MembershipId membershipId, MoneyAmount amount) {
        memberMoneyRepository.increaseBalance(Long.parseLong(membershipId.getMembershipId()), amount.value());
    }

    @Override
    public boolean decreaseBalance(MemberMoney.MembershipId membershipId, MoneyAmount amount) {
        return memberMoneyRepository.decreaseBalance(Long.parseLong(membershipId.getMembershipId()), amount.value()) == 1;
    }

    @Override
    public void createMemberMoney(MemberMoney.MembershipId membershipId, MemberMoney.MoneyAggregateIdentifier moneyAggregateIdentifier) {
        memberMoneyRepository.openWallet(Long.parseLong(membershipId.getMembershipId()), moneyAggregateIdentifier.getAggregateIdentifier());
    }

    @Override
    public MemberMoney.MoneyAggregateIdentifier getMoneyAggregateIdentifier(MemberMoney.MembershipId membershipId) {
        return new MemberMoney.MoneyAggregateIdentifier(memberMoneyRepository.findByMembershipId(Long.parseLong(membershipId.getMembershipId()))
                .map(MemberMoneyJpaEntity::getAggregateIdentifier)
                .orElse(""));
    }

    @Override
    public List<MemberMoney> getMemberMoneys(List<String> membershipIds) {
        List<Long> longList = membershipIds.stream().map(Long::parseLong).toList();

        return memberMoneyRepository.findByMembershipIdIn(longList).stream()
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
        return new MoneyChangingRequest(
                new RequestId(entity.getRequestId()),
                entity.getMembershipId().toString(),
                entity.getChangeType(),
                new MoneyAmount(entity.getAmount()),
                entity.getStatus()
        );
    }

}
