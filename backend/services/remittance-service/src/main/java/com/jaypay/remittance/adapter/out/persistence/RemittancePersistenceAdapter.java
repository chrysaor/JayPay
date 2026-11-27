package com.jaypay.remittance.adapter.out.persistence;

import com.jaypay.common.PersistenceAdapter;
import com.jaypay.remittance.application.port.out.RemittancePort;
import com.jaypay.remittance.domain.NewRemittance;
import com.jaypay.remittance.domain.Receiver;
import com.jaypay.remittance.domain.Remittance;
import com.jaypay.remittance.domain.RemittanceStatus;
import com.jaypay.remittance.domain.RequestId;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
public class RemittancePersistenceAdapter implements RemittancePort {

    private static final String MEMBER = "MEMBER";
    private static final String BANK_ACCOUNT = "BANK_ACCOUNT";

    private final SpringDataRemittanceRepository remittanceRepository;

    @Override
    public Optional<Remittance> findByRequestId(RequestId requestId) {
        return remittanceRepository.findByRequestId(requestId.value()).map(RemittancePersistenceAdapter::toDomain);
    }

    @Override
    public Remittance claim(NewRemittance remittance) {
        UUID remittanceId = UUID.randomUUID();
        long from = Long.parseLong(remittance.fromMembershipId());
        String requestId = remittance.requestId().value();
        switch (remittance.receiver()) {
            case Receiver.Member member -> remittanceRepository.claim(remittanceId, requestId, from, MEMBER,
                    Long.parseLong(member.membershipId()), null, null, remittance.amount());
            case Receiver.BankAccount account -> remittanceRepository.claim(remittanceId, requestId, from, BANK_ACCOUNT,
                    null, account.bankName(), account.bankAccountNumber(), remittance.amount());
        }
        return findByRequestId(remittance.requestId()).orElseThrow();
    }

    @Override
    public boolean advance(UUID remittanceId, RemittanceStatus from, RemittanceStatus to) {
        return remittanceRepository.advance(remittanceId, from.name(), to.name()) == 1;
    }

    @Override
    public Remittance find(UUID remittanceId) {
        return toDomain(remittanceRepository.findById(remittanceId).orElseThrow());
    }

    @Override
    public List<Remittance> findSentOrReceivedBy(String membershipId) {
        long member = Long.parseLong(membershipId);
        return remittanceRepository.findByFromMembershipIdOrToMembershipIdOrderByRequestedAtDesc(member, member).stream()
                .map(RemittancePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<SweptRemittance> sweep(Duration retryAfter, int attempts) {
        List<String> unfinished = Arrays.stream(RemittanceStatus.values()).filter(status -> !status.finished()).map(Enum::name).toList();
        return remittanceRepository.sweep(unfinished, retryAfter.toMillis() / 1000.0, attempts).stream()
                .map(entity -> new SweptRemittance(toDomain(entity), entity.getSweepAttempts()))
                .toList();
    }

    private static Remittance toDomain(RemittanceJpaEntity entity) {
        Receiver receiver = MEMBER.equals(entity.getRemittanceType())
                ? new Receiver.Member(entity.getToMembershipId().toString())
                : new Receiver.BankAccount(entity.getToBankName(), entity.getToBankAccountNumber());
        NewRemittance request = new NewRemittance(new RequestId(entity.getRequestId()), entity.getFromMembershipId().toString(),
                receiver, entity.getAmount());
        return new Remittance(entity.getRemittanceId(), request, entity.getRemittanceStatus());
    }
}
